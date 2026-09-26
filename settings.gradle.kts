pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "KikuGie Releases"
            url = uri("https://maven.kikugie.dev/releases")
        }
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

// One build per Minecraft version and loader: project versions/<mc>-<loader>, built by build.<loader>.gradle.kts
// from the shared src/ (version- and loader-specific code sits in //? if ... comments, see stonecutter.gradle.kts).
stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version).buildscript("build.$loader.gradle.kts")
        }
        match("26.2", "fabric", "neoforge")
        match("26.3", "fabric", "neoforge")
        // The version the committed sources are written for (its //? blocks are the uncommented ones).
        vcsVersion = "26.2-fabric"
    }
}

rootProject.name = "YungLightUI"
