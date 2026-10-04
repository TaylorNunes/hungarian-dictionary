import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Reads the dictionary database built by scripts/build_data.py. The SQLite driver is passed in:
// the app uses Android's own SQLite, the tests use the bundled one.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(libs.androidx.sqlite)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.icu4j)
            implementation(libs.xz)
        }
    }
}

// The golden parity test reads the built database and the web search's results for the same data.
// Defaults suit a local run after `python3 scripts/build_data.py` and the golden vitest run;
// CI passes -Pszokert.db=… -Pszokert.golden=…. The test is skipped when either is missing.
val repo = rootDir.resolve("..")
val dataDir = file(findProperty("szokert.data") ?: repo.resolve("public/data"))
val goldenFile = file(findProperty("szokert.golden") ?: rootDir.resolve("build/golden.json"))
tasks.withType<Test>().configureEach {
    systemProperty("szokert.data", dataDir.path)
    systemProperty("szokert.golden", goldenFile.path)
    // Rerun when the data or the golden results change, not only when code does.
    inputs.files(fileTree(dataDir) { include("manifest.json") }).withPropertyName("dataManifest").optional()
    inputs.files(goldenFile).withPropertyName("golden").optional()
}
