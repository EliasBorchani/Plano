plugins {
    alias(libs.plugins.plano.kmp.feature)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.domain.events.api)
        implementation(projects.utilities.kotlinExt)
    }
}
