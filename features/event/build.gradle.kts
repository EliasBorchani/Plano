plugins {
    alias(libs.plugins.plano.kmp.feature)
}

kotlin {
    sourceSets.commonMain.dependencies {
        api(projects.domain.events.api)
        api(projects.domain.polls.api)
        api(projects.domain.auth.api)
        implementation(projects.utilities.kotlinExt)
    }
}
