// NeoForge build of one Stonecutter node (versions/<mc>-neoforge), from the same src/ as the Fabric build.
// Loader-specific code sits in `//? if fabric {` / `//? if neoforge {` blocks; dihclient.platform.DihPlatform is the
// seam. Resources the Fabric node generates (UI icons, packet schemas) and the JourneyMap API it extracts are reused.
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    id("net.neoforged.moddev") version "2.0.147"
}

val mcVersion: String = sc.current.version
fun dep(key: String): String = sc.properties["deps.$key"]
val fabricNode = project(":$mcVersion-fabric")

// Optional: build outside a synced folder, as for the Fabric nodes (-PdihBuildDir=C:/dih-build).
providers.gradleProperty("dihBuildDir").orNull?.let { layout.buildDirectory.set(file(it).resolve(project.name)) }

base {
    archivesName = properties["archives_base_name"] as String
    // "<mod>-<mc>-neoforge" (e.g. 5.1-26.2-neoforge). The update checker picks its jar by this suffix.
    version = libs.versions.mod.version.get() + "-" + mcVersion + "-neoforge" +
        (if (project.hasProperty("release")) "" else "-dev")
    group = properties["maven_group"] as String
}

repositories {
    mavenCentral()
    maven { url = uri("https://api.modrinth.com/maven") }
    maven {
        name = "babbaj-repo"
        url = uri("https://babbaj.github.io/maven/")
    }
}

sourceSets {
    val main by getting
    // Baritone API and the compile-only mod stubs, as in the Fabric build.
    val api by creating
    val schematica_api by creating
    main.compileClasspath += api.output + schematica_api.output
    main.runtimeClasspath += api.output
    val launch by creating {
        compileClasspath += main.output + api.output
        runtimeClasspath += main.output + api.output
    }
    main.runtimeClasspath += launch.output
}

neoForge {
    version = dep("neoforge")
    addModdingDependenciesTo(sourceSets["api"])
    addModdingDependenciesTo(sourceSets["schematica_api"])
    addModdingDependenciesTo(sourceSets["launch"])

    mods {
        register("dih") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["api"])
            sourceSet(sourceSets["launch"])
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run/neoforge-$mcVersion")
            // -PdihAudit: apply every mixin on the first tick, log the result and quit (see DihNeoForgeMod).
            if (project.hasProperty("dihAudit")) systemProperty("dih.auditAndExit", "true")
        }
    }
}

// Libraries shipped inside the mod jar (jar-in-jar), also on the dev runtime classpath.
val bundled = listOf(
    "io.netty:netty-handler-proxy:4.1.118.Final",
    "io.netty:netty-codec-socks:4.1.118.Final",
    "de.florianreuth:waybackauthlib:1.1.0",
    "com.github.weisj:jsvg:2.1.0",
    "org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5",
    "dev.babbaj:nether-pathfinder:1.4.1",
)

dependencies {
    for (lib in bundled) {
        implementation(lib)
        jarJar(lib)
    }
    "launchImplementation"(sourceSets["main"].compileClasspath)

    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    compileOnly("net.java.dev.jna:jna:5.13.0")
    compileOnly("net.java.dev.jna:jna-platform:5.13.0")
    // Compat code for optional mods: compile-only, like the Fabric build.
    compileOnly("maven.modrinth:lithium:${dep("lithium")}")
    compileOnly("maven.modrinth:replaymod:${dep("replaymod")}")
    compileOnly(files(fabricNode.layout.buildDirectory.file("journeymap-api/journeymap-api.jar"))
        .builtBy(fabricNode.tasks.named("extractJourneyMapApi")))
}

// ModDevGradle runs its asset downloader on a Java 21 toolchain by default; use the JDK running the build (25).
tasks.named("downloadAssets") {
    (this as net.neoforged.nfrtgradle.DownloadAssets).javaExecutable
        .set(File(System.getProperty("java.home"), "bin/java").absolutePath)
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

        val propertyMap = mapOf(
            "version" to project.version.toString(),
            "mc_version" to mcVersion,
            "neoforge_version" to dep("neoforge"),
        )
        inputs.properties(propertyMap)
        filteringCharset = "UTF-8"
        filesMatching("META-INF/neoforge.mods.toml") { expand(propertyMap) }

        exclude("fabric.mod.json", "addon-template.mixins.json", "assets/template/**")
    }

    // Mixin classes that are Fabric-only (their sources compile to nothing here) come out of the mixin configs.
    val filterMixinConfigs by registering {
        dependsOn("processResources", "processLaunchResources", "compileJava", "compileLaunchJava")
        val configs = listOf(
            layout.buildDirectory.file("resources/main/dih.mixins.json"),
            layout.buildDirectory.file("resources/launch/dih-baritone.mixins.json"),
        )
        val classDirs = files(sourceSets["main"].output.classesDirs, sourceSets["launch"].output.classesDirs)
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
                config.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(json)) + "\n", Charsets.UTF_8)
                if (dropped.isNotEmpty()) logger.lifecycle("${config.name}: Fabric-only mixins left out: $dropped")
            }
        }
    }

    named("prepareClientRun") { dependsOn(filterMixinConfigs) }

    jar {
        dependsOn(filterMixinConfigs)
        inputs.property("archivesName", project.base.archivesName.get())
        from(sourceSets["api"].output, sourceSets["launch"].output)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(rootProject.file("LICENSE")) {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
        from(rootProject.file("licenses")) { into("META-INF/licenses") }
        from(rootProject.file("NOTICE.md")) { into("META-INF") }
        // ModMenu is Fabric-only; its API stubs are compile-only.
        exclude("com/terraformersmc/**")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.add("-Xlint:-restricted")
    }
}
