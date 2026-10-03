pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
// The `org.gradle.toolchains.foojay-resolver-convention` plugin used to be
// applied here to auto-provision a JDK. It was removed because nothing in this
// build requests a toolchain — there is no `toolchain { }` block anywhere, and
// the project compiles against the JDK that runs Gradle (Java 11 source
// compatibility, JDK 25 pinned in CI via setup-java) — so it provisioned
// nothing while adding a settings-classpath resolution that failed
// dependency verification on every clean CI runner:
//
//   Dependency verification failed for configuration 'classpath'
//     - foojay-resolver-1.0.0.jar (org.gradle.toolchains:foojay-resolver:1.0.0)
//     - foojay-resolver-1.0.0.module
//
// `--write-verification-metadata` does not write that configuration, so the
// entry could not simply be regenerated. Re-add a resolver only alongside a
// real `toolchain { }` block, and pin it in gradle/verification-metadata.xml at
// the same time.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PayVoice"
include(":app")
