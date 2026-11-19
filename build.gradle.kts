// =====================================================================
//  CasinoDX — Premium Minecraft Casino System
//  Author: DravoraX
// =====================================================================

plugins {
    `java-library`
}

group = "com.dravorax"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.isFork = false
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/creatorfromhell/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    // Both JDBC drivers are declared under `libraries:` in plugin.yml and are
    // downloaded by the server's library loader, so they are compile-only here.
    compileOnly("org.xerial:sqlite-jdbc:3.45.3.0")
    compileOnly("com.mysql:mysql-connector-j:9.2.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.19") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("project" to mapOf("version" to project.version))
    }
}

tasks.jar {
    archiveBaseName.set("CasinoDX")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
