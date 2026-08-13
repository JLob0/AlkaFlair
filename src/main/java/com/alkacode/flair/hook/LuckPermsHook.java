package com.alkacode.flair.hook;

import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * So usado pro `grant-permission-on-unlock` (config.yml) - quando uma tag/medalha e
 * desbloqueada, tenta conceder a permission correspondente no LuckPerms tambem, alem
 * de salvar no proprio banco do AlkaFlair (que continua sendo a fonte de verdade real
 * do "tem ou nao tem" - isso e so uma conveniencia pra quem quer checar a mesma
 * permission em outro lugar). Reflection + presence-check, nunca importa
 * net.luckperms.api direto (mesmo padrao do LuckPermsHook do AlkaDrop) - toda falha
 * cai no catch e vira no-op silencioso, nunca quebra o desbloqueio em si.
 */
public final class LuckPermsHook {

    private final Logger logger;
    private final boolean present;

    public LuckPermsHook(Logger logger) {
        this.logger = logger;
        this.present = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }

    public boolean isPresent() {
        return present;
    }

    public void grantPermission(UUID uuid, String permission) {
        if (!present || permission == null || permission.isBlank()) {
            return;
        }
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);

            Object userFuture = userManager.getClass().getMethod("loadUser", UUID.class).invoke(userManager, uuid);
            Object user = ((java.util.concurrent.CompletableFuture<?>) userFuture).join();
            if (user == null) {
                return;
            }

            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
            Method nodeBuilder = nodeClass.getMethod("builder", String.class);
            Object builder = nodeBuilder.invoke(null, permission);
            Object node = builder.getClass().getMethod("build").invoke(builder);

            Object nodeData = user.getClass().getMethod("data").invoke(user);
            nodeData.getClass().getMethod("add", nodeClass).invoke(nodeData, node);

            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            userManager.getClass().getMethod("saveUser", userClass).invoke(userManager, user);
        } catch (Throwable t) {
            logger.log(Level.FINE, "Hook LuckPerms falhou ao conceder '" + permission + "' pra " + uuid + ": " + t, t);
        }
    }
}
