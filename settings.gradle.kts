pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        // The JetBrains lifecycle-viewmodel fork depends on androidx artifacts hosted by Google.
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "plano"

// Utilities
include(":utilities:kotlin-ext")
include(":utilities:network")
include(":utilities:presentation")

// Wire format shared by the client data modules and the server
include(":contract")

// Domain: api module + data module per bounded context
include(":domain:auth:api")
include(":domain:auth:data")
include(":domain:events:api")
include(":domain:events:data")
include(":domain:polls:api")
include(":domain:polls:data")
// Pure rules for now (clean, identify, parse); the server fetches pages. A :data module comes with the place poll.
include(":domain:links:api")
// Places from OpenStreetMap (Nominatim, server side) and the matching rule; :data comes with the place poll.
include(":domain:places:api")
// Groups: rules only for now (name, invite link limits); the server stores them, a :data module comes with the screens.
include(":domain:groups:api")

// Features (VM + state, commonMain only)
include(":features:signin")
include(":features:events")
include(":features:event")

// Web umbrella: the JS library React consumes
include(":bridge-web")

// Backend
include(":server")
