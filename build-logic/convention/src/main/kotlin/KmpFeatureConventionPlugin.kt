import ext.lib
import ext.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention for a :features:<name> module.
 * Native UI per platform: no UI code here, only VM + state + intent + event.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("plano.kmp.library")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.commonMain.dependencies {
                    api(project(":utilities:presentation"))
                    implementation(libs.lib("koin-core"))
                }
            }
        }
    }
}
