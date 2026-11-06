plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

// The web umbrella: aggregates features and data modules into one ES module
// with TypeScript definitions, consumed by apps/web as the npm package "plano-shared".
kotlin {
    js {
        outputModuleName = "plano-shared"
        browser()
        useEsModules()
        binaries.library()
        generateTypeScriptDefinitions()
        compilerOptions {
            target.set("es2015")
            optIn.add("kotlin.js.ExperimentalJsExport")
        }
    }

    sourceSets {
        jsMain.dependencies {
            implementation(projects.features.signin)
            implementation(projects.features.events)
            implementation(projects.features.event)

            implementation(projects.domain.auth.data)
            implementation(projects.domain.events.data)
            implementation(projects.domain.polls.data)
            implementation(projects.utilities.network)
            implementation(projects.contract)

            implementation(libs.koin.core)
        }
    }
}
