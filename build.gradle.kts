import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.alkacode"
version = "1.0.14"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    // banco/HikariCP e GUI base vem do AlkaCore (DatabaseProvider/BaseGui) - AlkaFlair
    // nao abre conexao JDBC propria nem registra o proprio GuiListener.
    compileOnly("com.alkacode:AlkaCore:1.0.10")
    // compra de tags e paga em qualquer moeda da AlkaEconomy (config-driven).
    compileOnly("com.alkacode:AlkaEconomy:1.0.8")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
    // tag flutuante 3D acima da cabeca (TextDisplay via packet) - softdepend, ver
    // com.alkacode.flair.floating. PacketEvents ja e plugin permanente da rede
    // (nao precisa instalar nada novo no host). Confirme que a versao do jar no host
    // e compativel com essa API antes de subir - reflection puro, plugin so desativa
    // o recurso (sem crashar) se o plugin nao estiver presente.
    compileOnly("com.github.retrooper:packetevents-spigot:2.13.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", project.version)
    filesMatching("plugin.yml") {

        expand("version" to project.version)

    }
}
