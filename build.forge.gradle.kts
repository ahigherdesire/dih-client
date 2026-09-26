// Forge build of one Stonecutter node (versions/<mc>-forge), from the same src/ as the Fabric and NeoForge builds.
// Loader-specific code sits in `//? if forge {` blocks behind dihclient.platform. ForgeGradle 7 (Mavenizer).
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    id("net.minecraftforge.gradle") version "7.0.40"
    id("net.minecraftforge.jarjar") version "0.2.3"
}

val mcVersion: String = sc.current.version
fun dep(key: String): String = sc.properties["deps.$key"]
val fabricNode = project(":$mcVersion-fabric")

// Optional: build outside a synced folder, as for the other nodes (-PdihBuildDir=C:/dih-build).
providers.gradleProperty("dihBuildDir").orNull?.let { layout.buildDirectory.set(file(it).resolve(project.name)) }

base {
    archivesName = properties["archives_base_name"] as String
    // "<mod>-<mc>-forge" (e.g. 5.1-26.2-forge). The update checker picks its jar by this suffix.
    version = libs.versions.mod.version.get() + "-" + mcVersion + "-forge" +
        (if (project.hasProperty("release")) "" else "-dev")
    group = properties["maven_group"] as String
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
    maven { url = uri("https://api.modrinth.com/maven") }
    maven {
        name = "babbaj-repo"
        url = uri("https://babbaj.github.io/maven/")
    }
}

// Forge's dev launcher makes every output folder its own module, so the mod has to be one folder: the api and
// launch sources (still Stonecutter source sets, for their version comments) compile into main, and main's
// resources land next to its classes.
val api: SourceSet by sourceSets.creating
val schematica_api: SourceSet by sourceSets.creating
val launch: SourceSet by sourceSets.creating
sourceSets.named("main") {
    java.srcDir(layout.projectDirectory.dir("build/generated/stonecutter/api/java"))
    java.srcDir(layout.projectDirectory.dir("build/generated/stonecutter/launch/java"))
    resources.srcDir(rootProject.file("src/launch/resources"))
    compileClasspath += schematica_api.output
    output.setResourcesDir(java.destinationDirectory.get().asFile)
}

val forgeDep = minecraft.dependency("net.minecraftforge:forge:${dep("forge")}")

minecraft {
    runs {
        configureEach {
            workingDir.convention(rootProject.layout.projectDirectory.dir("run/forge-$mcVersion"))
            args("--mixin.config=dih.mixins.json", "--mixin.config=dih-baritone.mixins.json")
            // -PdihAudit: apply every mixin, play a singleplayer world and quit (see DihForgeMod).
            if (project.hasProperty("dihAudit")) systemProperty("dih.auditAndExit", "true")
            providers.gradleProperty("dihDisableMixins").orNull?.let { systemProperty("dih.disableMixins", it) }
        }
        register("client")
    }
}

// The jar-in-jar jar is the release jar; the plain one is "-slim".
jarJar.register {
    archiveClassifier = null
}
tasks.named<Jar>("jar") {
    archiveClassifier = "slim"
}

// Libraries shipped inside the mod jar (jar-in-jar). Forge has Mixin but not MixinExtras.
val bundled = listOf(
    "io.netty:netty-handler-proxy:4.1.118.Final",
    "io.netty:netty-codec-socks:4.1.118.Final",
    "de.florianreuth:waybackauthlib:1.1.0",
    "com.github.weisj:jsvg:2.1.0",
    "org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5",
    "dev.babbaj:nether-pathfinder:1.4.1",
    "io.github.llamalad7:mixinextras-forge:0.5.4",
)

dependencies {
    implementation(forgeDep)
    "apiImplementation"(forgeDep)
    "schematica_apiImplementation"(forgeDep)
    "launchImplementation"(forgeDep)
    for (lib in bundled) {
        implementation(lib)
        "jarJar"(lib)
    }

    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("net.java.dev.jna:jna:5.13.0")
    compileOnly("net.java.dev.jna:jna-platform:5.13.0")
    // Compat code for optional mods: compile-only, like the other builds.
    compileOnly("maven.modrinth:lithium:${dep("lithium")}")
    compileOnly("maven.modrinth:replaymod:${dep("replaymod")}")
    compileOnly(files(fabricNode.layout.buildDirectory.file("journeymap-api/journeymap-api.jar"))
        .builtBy(fabricNode.tasks.named("extractJourneyMapApi")))
}

// Unit and game tests run in the Fabric builds; the sources are loader-independent.
tasks.named("compileTestJava") { enabled = false }
tasks.named("test") { enabled = false }

val fabricGeneratedResources = fabricNode.layout.buildDirectory.dir("generated/resources/dih/main")

tasks {
    processResources {
        dependsOn(fabricNode.tasks.named("generateVanillaUiAssets"), fabricNode.tasks.named("generateDihInspectorMappings"),
            fabricNode.tasks.named("generateDihPacketSchemas"))
        from(fabricGeneratedResources)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        val propertyMap = mapOf(
            "version" to project.version.toString(),
            "mc_version" to mcVersion,
            "forge_version" to dep("forge").substringAfter('-'),
        )
        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"
        filesMatching("META-INF/mods.toml") { expand(propertyMap) }

        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml", "addon-template.mixins.json", "assets/template/**")

        // Forge only loads a mod's assets as a resource pack when it has pack metadata.
        val packFormat = mapOf("26.2" to 88, "26.3" to 97).getValue(mcVersion)
        inputs.property("packFormat", packFormat)
        doLast {
            destinationDir.resolve("pack.mcmeta").writeText(
                "{\"pack\": {\"description\": \"DIH Client resources\", \"min_format\": $packFormat, \"max_format\": $packFormat}}" + System.lineSeparator())
        }
    }

    // Mixin classes for another loader (their sources compile to nothing here) come out of the mixin configs.
    val filterMixinConfigs by registering {
        dependsOn("processResources", "compileJava")
        val out = sourceSets["main"].java.destinationDirectory
        val configs = listOf(out.file("dih.mixins.json"), out.file("dih-baritone.mixins.json"))
        val classDirs = files(out)
        doLast {
            for (config in configs.map { it.get().asFile }.filter { it.isFile }) {
                @Suppress("UNCHECKED_CAST")
                val json = JsonSlurper().parse(config) as MutableMap<String, Any?>
                val pkg = (json["package"] as String).replace('.', '/')
                val dropped = mutableListOf<String>()
                for (side in listOf("mixins", "client", "server")) {
                    @Suppress("UNCHECKED_CAST")
                    val names = json[side] as? List<String> ?: continue
                    json[side] = names.filter { name ->
                        val present = classDirs.any { it.resolve("$pkg/${name.replace('.', '/')}.class").isFile }
                        if (!present) dropped += name
                        present
                    }
                }
                // Forge's Mixin doesn't know JAVA_25 yet (the Baritone config already says JAVA_21).
                if (json["compatibilityLevel"] == "JAVA_25") json["compatibilityLevel"] = "JAVA_21"
                json.putIfAbsent("minVersion", "0.8")
                config.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)) + "\n", Charsets.UTF_8)
                if (dropped.isNotEmpty()) logger.lifecycle("${config.name}: other loaders' mixins left out: $dropped")
            }
        }
    }

    named("compileJava") { dependsOn("stonecutterGenerateApi", "stonecutterGenerateLaunch") }
    named("classes") { finalizedBy(filterMixinConfigs) }
    matching { it.name.startsWith("run") }.configureEach { dependsOn(filterMixinConfigs) }

    named<Jar>("jar") {
        dependsOn(filterMixinConfigs)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(rootProject.file("LICENSE")) { rename { "${it}_DIH Client" } }
        from(rootProject.file("licenses")) { into("META-INF/licenses") }
        from(rootProject.file("NOTICE.md")) { into("META-INF") }
        // ModMenu is Fabric-only; its API stubs are compile-only.
        exclude("com/terraformersmc/**")
        manifest {
            attributes["MixinConfigs"] = "dih.mixins.json,dih-baritone.mixins.json"
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.add("-Xlint:-restricted")
    }
}
