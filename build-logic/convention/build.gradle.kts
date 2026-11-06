plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "plano.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpFeature") {
            id = "plano.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
    }
}
