import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.messagecheck"
version = "0.1.0"

description = "Message moderation plugin for Bukkit, Folia and Velocity"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/sonatype-snapshots/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.md-5.net/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
    annotationProcessor("com.velocitypowered:velocity-api:3.1.1")

    implementation("io.netty:netty-all:4.1.106.Final")
    implementation("com.github.ben-manes.caffeine:caffeine:2.9.3")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("redis.clients:jedis:5.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_11)) {
        options.release.set(8)
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filesMatching(listOf("plugin.yml", "velocity-plugin.json")) {
        expand(
            "version" to project.version
        )
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val shadePackage = "com.messagecheck.shaded"

fun ShadowJar.relocateDependency(pkg: String) {
    relocate(pkg, "$shadePackage.${pkg.replace('.', '_')}")
}


tasks.shadowJar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    relocateDependency("com.google.common")
    relocateDependency("com.google.gson")
    relocateDependency("org.yaml")
    relocateDependency("com.zaxxer.hikari")
    relocateDependency("redis.clients.jedis")
    relocateDependency("io.netty")
    relocateDependency("okhttp3")
    relocateDependency("okio")
    relocateDependency("com.github.benmanes.caffeine")
    // Avoid calling minimize(); the plugin entry classes are discovered via
    // descriptor metadata and would be stripped from the shaded jar.
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
