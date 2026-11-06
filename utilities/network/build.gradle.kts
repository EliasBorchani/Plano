plugins {
    alias(libs.plugins.plano.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.utilities.kotlinExt)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.contentneg)
            implementation(libs.ktor.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
    }
}
