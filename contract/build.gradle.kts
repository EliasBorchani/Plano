plugins {
    alias(libs.plugins.plano.kmp.library)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.serialization.json)
    }
}
