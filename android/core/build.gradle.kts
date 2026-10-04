import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Dictionary logic shared by every platform: ported from src/lib/ and kept in step with it by the
// shared fixtures in scripts/fixtures. Only a jvm() target for now (used by the Android app);
// an iOS target can be added later without moving code.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.icu4j)
        }
    }
}

val fixtures = rootDir.resolve("../scripts/fixtures").absolutePath
tasks.withType<Test>().configureEach {
    systemProperty("szokert.fixtures", fixtures)
}
