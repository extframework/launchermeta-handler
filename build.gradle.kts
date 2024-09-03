import dev.extframework.gradle.common.commonUtil
import dev.extframework.gradle.common.dm.jobs
import dev.extframework.gradle.common.dm.resourceApi
import dev.extframework.gradle.common.extFramework

plugins {
    kotlin("jvm") version "1.9.21"

    id("dev.extframework.common") version "1.0.20"
}

group = "dev.extframework"
version = "1.1.4-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "7.2"
}

dependencies {
    jobs()
    resourceApi()
    commonUtil()

    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}

common {
    publishing {
        publication {
            artifactId = "launchermeta-handler"

            pom {
                name.set("Launcher Meta Handler")
                description.set("A handler for all Mojang launcher meta (ok maybe not all ;) )")
                url.set("https://github.com/extframework/launchermeta-handler")
            }
        }
    }
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    repositories {
        mavenCentral()
        extFramework()
    }

    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }

    kotlin {
        explicitApi()
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation(kotlin("reflect"))
        testImplementation(kotlin("test"))
    }

    common {
        defaultJavaSettings()

        publishing {
            publication {
                withJava()
                withSources()
                withDokka()

                commonPom {
                    packaging = "jar"

                    withExtFrameworkRepo()
                    defaultDevelopers()
                    gnuLicense()
                    extFrameworkScm("launchermeta-handler")
                }
            }
            this.repositories {
                extFramework(credentials = propertyCredentialProvider)
            }
        }
    }
}