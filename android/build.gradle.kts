// Plugin versions live in gradle/libs.versions.toml; each module applies what it needs.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
