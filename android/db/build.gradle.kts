import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Reads the dictionary database built by scripts/build_data.py.
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
        }
    }
}
