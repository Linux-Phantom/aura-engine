import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar
import java.util.Properties

plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.compose") version "2.0.21"
    id("com.codingfeline.buildkonfig") version "0.15.2"
    id("org.jetbrains.compose") version "1.7.1"
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.material3)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

tasks.register("test") {
    group = "verification"
    description = "Runs the JVM test suite"
    dependsOn("jvmTest")
}

tasks.register<Jar>("jvmFatJar") {
    group = "distribution"
    description = "Builds the runnable Linux JVM daemon JAR with runtime dependencies"
    val jvmCompilation = kotlin.jvm().compilations.getByName("main")
    dependsOn("jvmJar")
    archiveFileName.set("aura-engine-all.jar")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.aura.core.daemon.AuraDaemonKt"
    }
    from(jvmCompilation.output)
    from({
        jvmCompilation.runtimeDependencyFiles.files.map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
    exclude("com/aura/core/BuildKonfig.class")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST")
}

tasks.register<JavaExec>("runBenchmark") {
    group = "application"
    description = "Runs the Aura benchmark entrypoint"
    classpath = kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
    mainClass.set("com.aura.core.network.AuraBenchmarkJvmKt")
}

tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Runs the interactive Aura CLI chat"
    classpath = kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles
    mainClass.set("com.aura.core.network.AuraCliKt")
}

tasks.register<JavaExec>("runClassifierTest") {
    group = "application"
    description = "Runs the AuraClassifier test runner"
    dependsOn("compileKotlinJvm")
    classpath = files(kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles,
        kotlin.jvm().compilations.getByName("main").output)
    mainClass.set("com.aura.core.network.AuraClassifierTestRunnerKt")
}

tasks.register<JavaExec>("runPredictorTest") {
    group = "application"
    description = "Runs the AuraPredictor test runner"
    dependsOn("compileKotlinJvm")
    classpath = files(kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles,
        kotlin.jvm().compilations.getByName("main").output)
    mainClass.set("com.aura.core.predictor.AuraPredictorRunnerKt")
}

tasks.register<JavaExec>("runFallbackTest") {
    group = "application"
    description = "Runs the AuraFallbackRouter test runner"
    dependsOn("compileKotlinJvm")
    classpath = files(kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles,
        kotlin.jvm().compilations.getByName("main").output)
    mainClass.set("com.aura.core.fallback.AuraFallbackRouterRunnerKt")
}
// Use an Exec-based task to run the assembled jar with dependencies for an interactive session
tasks.register<Exec>("runCliExec") {
    group = "application"
    description = "Runs the interactive Aura CLI chat (Exec, interactive)"
    dependsOn("jvmJar")
    commandLine = listOf("bash", "-lc", "java -cp build/libs/aura-core-jvm.jar:$(find ~/.gradle/caches/modules-2/files-2.1 -name '*.jar' | tr '\\n' ':') com.aura.core.network.AuraCliKt")
}
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the AURA Engine Compose Desktop application"
    dependsOn("compileKotlinJvm")
    classpath = files(kotlin.jvm().compilations.getByName("main").runtimeDependencyFiles,
        kotlin.jvm().compilations.getByName("main").output)
    mainClass.set("com.aura.core.MainKt")
}
buildkonfig {
    packageName = "com.aura.core"
    exposeObjectWithName = "BuildKonfig"

    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "GROQ_API_KEY", readLocalProperty("GROQ_API_KEY"))
        buildConfigField(FieldSpec.Type.STRING, "CEREBRAS_API_KEY", readLocalProperty("CEREBRAS_API_KEY"))
        buildConfigField(FieldSpec.Type.STRING, "DEEPSEEK_API_KEY", readLocalProperty("DEEPSEEK_API_KEY"))
        buildConfigField(FieldSpec.Type.STRING, "GEMINI_API_KEY", readLocalProperty("GEMINI_API_KEY"))
        buildConfigField(FieldSpec.Type.STRING, "CLAUDE_API_KEY", readLocalProperty("CLAUDE_API_KEY"))
        buildConfigField(FieldSpec.Type.STRING, "OPENAI_API_KEY", readLocalProperty("OPENAI_API_KEY"))
        buildConfigField(FieldSpec.Type.STRING, "OPENROUTER_API_KEY", readLocalProperty("OPENROUTER_API_KEY"))
        buildConfigField(FieldSpec.Type.STRING, "KIMI_API_KEY", readLocalProperty("KIMI_API_KEY"))
    }
}

fun readLocalProperty(key: String): String {
    val localPropertiesFile = rootProject.file("local.properties")
    if (!localPropertiesFile.exists()) {
        return ""
    }

    val properties = Properties()
    localPropertiesFile.inputStream().use { properties.load(it) }

    val candidates = listOf(
        key,
        key.uppercase(),
        key.lowercase(),
        key.replace("OPENAI_API_KEY", "OPEN_API_KEY"),
        key.replace("CLAUDE_API_KEY", "ClAUDE_API_KEY")
    )

    for (candidate in candidates) {
        val value = properties.getProperty(candidate)
        if (!value.isNullOrBlank()) {
            return value.trim()
        }
    }

    for ((name, value) in properties.entries) {
        val normalizedName = (name as String).uppercase().replace("-", "_")
        val normalizedKey = key.uppercase().replace("-", "_")
        if (normalizedName == normalizedKey || normalizedName == normalizedKey.replace("OPENAI_API_KEY", "OPEN_API_KEY")) {
            val propertyValue = value as? String
            if (!propertyValue.isNullOrBlank()) {
                return propertyValue.trim()
            }
        }
    }

    return ""
}
