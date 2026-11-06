import ext.lib
import ext.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Base convention for any KMP library module.
 *
 * Targets: JVM (server and fast tests) and JS (the web app). Android and iOS
 * targets are added here at the mobile milestone.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")

            // Several modules share a name (`domain:auth:api`, `domain:events:api`…): name the
            // jars after the full path so the server's distribution does not get duplicates.
            val uniqueName = path.trim(':').replace(':', '-')
            tasks.withType<Jar>().matching { it.name == "jvmJar" }.configureEach {
                archiveFileName.set("$uniqueName-jvm.jar")
            }

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(21)
                jvm()
                js {
                    // Tests run in Node; browser tests would need a headless Chrome for little gain.
                    browser { testTask { enabled = false } }
                    nodejs()
                    useEsModules()
                }
                applyDefaultHierarchyTemplate()

                compilerOptions {
                    optIn.add("kotlin.js.ExperimentalJsExport")
                }

                sourceSets.commonMain.dependencies {
                    implementation(libs.lib("kotlinx-coroutines-core"))
                }
                sourceSets.commonTest.dependencies {
                    implementation(kotlin("test"))
                    implementation(libs.lib("kotlinx-coroutines-test"))
                }
            }
        }
    }
}
