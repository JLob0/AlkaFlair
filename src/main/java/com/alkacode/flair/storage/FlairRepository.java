package com.alkacode.flair.storage;

import com.alkacode.core.api.DatabaseProvider;
import com.alkacode.core.database.AbstractRepository;
import com.alkacode.flair.model.PlayerFlairData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Unico ponto de acesso ao banco do AlkaFlair, sobre o DatabaseProvider do AlkaCore (R2). Blocking de proposito (R7) - chamadores rodam fora da main thread, ver FlairPlayerDataManager. */
public final class FlairRepository extends AbstractRepository {

    private final Logger logger;

    public FlairRepository(DatabaseProvider db, Logger logger) {
        super(db);
        this.logger = logger;
        createTables();
    }

    private void createTables() {
        try (Connection conn = db.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS alka_flair_players (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        equipped_tag_id VARCHAR(64),
                        own_prefix VARCHAR(64),
                        own_suffix VARCHAR(64),
                        tag_enabled INTEGER DEFAULT 1,
                        max_medal_slots INTEGER DEFAULT 3,
                        last_tag_switch_epoch BIGINT DEFAULT 0
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS alka_flair_unlocked_tags (
                        player_uuid VARCHAR(36) NOT NULL,
                        tag_id VARCHAR(64) NOT NULL,
                        unlocked_epoch BIGINT DEFAULT 0,
                        PRIMARY KEY (player_uuid, tag_id)
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS alka_flair_unlocked_medals (
                        player_uuid VARCHAR(36) NOT NULL,
                        medal_id VARCHAR(64) NOT NULL,
                        unlocked_epoch BIGINT DEFAULT 0,
                        PRIMARY KEY (player_uuid, medal_id)
                    )
                    """);
            addColumnIfMissing(conn, "alka_flair_unlocked_tags", "unlocked_epoch", "BIGINT DEFAULT 0");
            addColumnIfMissing(conn, "alka_flair_unlocked_medals", "unlocked_epoch", "BIGINT DEFAULT 0");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS alka_flair_equipped_medals (
                        player_uuid VARCHAR(36) NOT NULL,
                        medal_id VARCHAR(64) NOT NULL,
                        PRIMARY KEY (player_uuid, medal_id)
                    )
                    """);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao criar tabelas do AlkaFlair", e);
        }
    }

    /** Migracao best-effort pra bancos ja existentes de antes da coluna unlocked_epoch
     * (21/08) - o CREATE TABLE IF NOT EXISTS acima so cobre banco novo. Erro aqui so
     * significa "coluna ja existe", ignorado de proposito (nem SQLite nem MySQL tem um
     * "ADD COLUMN IF NOT EXISTS" universal via JDBC simples). */
    private void addColumnIfMissing(Connection conn, String table, String column, String definition) {
        try (var stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ignored) {
        }
    }

    public PlayerFlairData load(UUID uuid, int defaultMaxMedalSlots) {
        PlayerFlairData data = new PlayerFlairData(uuid, defaultMaxMedalSlots);
        String sql = "SELECT equipped_tag_id, own_prefix, own_suffix, tag_enabled, max_medal_slots, last_tag_switch_epoch FROM alka_flair_players WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    data.equippedTagId(rs.getString("equipped_tag_id"));
                    data.ownPrefix(rs.getString("own_prefix"));
                    data.ownSuffix(rs.getString("own_suffix"));
                    data.tagEnabled(rs.getInt("tag_enabled") != 0);
                    data.maxMedalSlots(rs.getInt("max_medal_slots") > 0 ? rs.getInt("max_medal_slots") : defaultMaxMedalSlots);
                    data.lastTagSwitchEpochSeconds(rs.getLong("last_tag_switch_epoch"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao carregar dados de " + uuid, e);
        }
        data.unlockedTagIds().addAll(loadIdSet(uuid, "alka_flair_unlocked_tags", "tag_id"));
        data.unlockedMedalIds().addAll(loadIdSet(uuid, "alka_flair_unlocked_medals", "medal_id"));
        data.equippedMedalIds().addAll(loadIdSet(uuid, "alka_flair_equipped_medals", "medal_id"));
        data.unlockedTagEpochs().putAll(loadUnlockedTagEpochs(uuid));
        data.unlockedMedalEpochs().putAll(loadUnlockedMedalEpochs(uuid));
        return data;
    }

    private Set<String> loadIdSet(UUID uuid, String table, String column) {
        Set<String> result = new HashSet<>();
        String sql = "SELECT " + column + " FROM " + table + " WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString(column));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao carregar " + table + " de " + uuid, e);
        }
        return result;
    }

    public void savePlayerRow(PlayerFlairData data) {
        String sql = upsert("alka_flair_players",
                new String[]{"player_uuid", "equipped_tag_id", "own_prefix", "own_suffix", "tag_enabled", "max_medal_slots", "last_tag_switch_epoch"},
                new String[]{"player_uuid"});
        try {
            execute(sql, ps -> {
                ps.setString(1, data.uuid().toString());
                ps.setString(2, data.equippedTagId());
                ps.setString(3, data.ownPrefix());
                ps.setString(4, data.ownSuffix());
                ps.setInt(5, data.tagEnabled() ? 1 : 0);
                ps.setInt(6, data.maxMedalSlots());
                ps.setLong(7, data.lastTagSwitchEpochSeconds());
            });
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao salvar dados de " + data.uuid(), e);
        }
    }

    public void addUnlockedTag(UUID uuid, String tagId) {
        insertIgnoreWithTimestamp("alka_flair_unlocked_tags", "tag_id", uuid, tagId);
    }

    public void removeUnlockedTag(UUID uuid, String tagId) {
        deleteId("alka_flair_unlocked_tags", "tag_id", uuid, tagId);
    }

    /** epoch (segundos) em que o jogador desbloqueou cada tag/medalha - 0 se nunca
     * registrado (unlocks de antes da coluna existir, 21/08). */
    public Map<String, Long> loadUnlockedTagEpochs(UUID uuid) {
        return loadEpochMap(uuid, "alka_flair_unlocked_tags", "tag_id");
    }

    public Map<String, Long> loadUnlockedMedalEpochs(UUID uuid) {
        return loadEpochMap(uuid, "alka_flair_unlocked_medals", "medal_id");
    }

    private Map<String, Long> loadEpochMap(UUID uuid, String table, String idColumn) {
        Map<String, Long> result = new HashMap<>();
        String sql = "SELECT " + idColumn + ", unlocked_epoch FROM " + table + " WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(idColumn), rs.getLong("unlocked_epoch"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao carregar unlocked_epoch de " + table + " para " + uuid, e);
        }
        return result;
    }

    /** INSERT direto (nao via upsert()) de proposito: com unlocked_epoch a mais que as
     * colunas de chave, upsert() vira "ON CONFLICT DO UPDATE" (MySQL/SQLite), o que
     * reescreveria a data original toda vez que /tags add repetisse um id ja
     * desbloqueado. Aqui e sempre "insere so na primeira vez, ignora depois". */
    private void insertIgnoreWithTimestamp(String table, String idColumn, UUID uuid, String id) {
        String sql = (db.isSQLite() ? "INSERT OR IGNORE INTO " : "INSERT IGNORE INTO ")
                + table + " (player_uuid, " + idColumn + ", unlocked_epoch) VALUES (?, ?, ?)";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, id);
            ps.setLong(3, System.currentTimeMillis() / 1000L);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao inserir em " + table + " para " + uuid, e);
        }
    }

    public void addUnlockedMedal(UUID uuid, String medalId) {
        insertIgnoreWithTimestamp("alka_flair_unlocked_medals", "medal_id", uuid, medalId);
    }

    public void removeUnlockedMedal(UUID uuid, String medalId) {
        deleteId("alka_flair_unlocked_medals", "medal_id", uuid, medalId);
    }

    public void addEquippedMedal(UUID uuid, String medalId) {
        insertIgnore("alka_flair_equipped_medals", "medal_id", uuid, medalId);
    }

    public void removeEquippedMedal(UUID uuid, String medalId) {
        deleteId("alka_flair_equipped_medals", "medal_id", uuid, medalId);
    }

    private void insertIgnore(String table, String column, UUID uuid, String id) {
        String sql = upsert(table, new String[]{"player_uuid", column}, new String[]{"player_uuid", column});
        try {
            execute(sql, ps -> {
                ps.setString(1, uuid.toString());
                ps.setString(2, id);
            });
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao inserir em " + table + " para " + uuid, e);
        }
    }

    private void deleteId(String table, String column, UUID uuid, String id) {
        String sql = "DELETE FROM " + table + " WHERE player_uuid = ? AND " + column + " = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao remover de " + table + " para " + uuid, e);
        }
    }
}
