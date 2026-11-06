plugins {
    alias(libs.plugins.plano.kmp.library)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(libs.androidx.lifecycle.viewmodel)
    }
}
