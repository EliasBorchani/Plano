plugins {
    alias(libs.plugins.plano.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.domain.polls.api)
            implementation(projects.contract)
            implementation(projects.utilities.network)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}
