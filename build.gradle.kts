plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("io.freefair.lombok") version "9.5.0"
    id("com.gradleup.shadow") version "9.0.0"
}

group = "ru.deelter"
version = "1.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    runServer {
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }

    shadowJar {
        archiveClassifier = ""
        relocate("com.github.benmanes.caffeine", "ru.deelter.waterphysics.libs.caffeine")
        // Relocate bStats into the plugin package to avoid conflicts with other plugins.
        relocate("org.bstats", "ru.deelter.waterphysics.libs.bstats")
        minimize {
            exclude(dependency("com.github.ben-manes.caffeine:.*"))
            exclude(dependency("org.bstats:.*"))
        }
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    build {
        dependsOn(shadowJar)
    }
}
