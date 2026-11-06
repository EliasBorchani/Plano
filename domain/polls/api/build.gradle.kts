plugins {
    alias(libs.plugins.plano.kmp.library)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.kotlinx.datetime)
    }
}
