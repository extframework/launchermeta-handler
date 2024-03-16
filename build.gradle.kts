plugins {
    kotlin("jvm") version "1.9.21"

    id("signing")
    id("maven-publish")
    id("org.jetbrains.dokka") version "1.9.10"
}

group = "net.yakclient"
version = "1.1-SNAPSHOT"

tasks.wrapper {
    gradleVersion = "7.2"
}

dependencies {
    implementation("com.durganmcbroom:jobs:1.2-SNAPSHOT")
    implementation("net.yakclient:common-util:1.1-SNAPSHOT")
    implementation("com.durganmcbroom:resource-api:1.0-SNAPSHOT")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    repositories {
        mavenCentral()
        maven {
            isAllowInsecureProtocol = true
            url = uri("http://maven.yakclient.net/snapshots")
        }
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

    tasks.compileKotlin {
        destinationDirectory.set(tasks.compileJava.get().destinationDirectory.asFile.get())


        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.compileTestKotlin {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    tasks.test {
        useJUnitPlatform()
    }

    tasks.compileJava {
        targetCompatibility = "17"
        sourceCompatibility = "17"
    }


    publishing {
        repositories {
            if (project.hasProperty("maven-user") && project.hasProperty("maven-secret")) maven {
                logger.quiet("Maven user and password found.")
                val repo = if ((version as String).endsWith("-SNAPSHOT")) "snapshots" else "releases"

                isAllowInsecureProtocol = true

                url = uri("http://maven.yakclient.net/$repo")

                credentials {
                    username = project.findProperty("maven-user") as String
                    password = project.findProperty("maven-secret") as String
                }
                authentication {
                    create<BasicAuthentication>("basic")
                }
            } else logger.quiet("Maven user and password not found.")
        }
    }
}

task<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

task<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaJavadoc)
}
publishing {
    publications {
        create<MavenPublication>("launchermeta-handler-maven") {
            from(components["java"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            artifactId = "launchermeta-handler"

            pom {
                name.set("Launcher Meta Handler")
                description.set("A handler for all Mojang launcher meta (ok maybe not all ;) )")
                url.set("https://github.com/yakclient/launchermeta-handler")

                packaging = "jar"

                withXml {
                    val repositoriesNode = asNode().appendNode("repositories")
                    val yakclientRepositoryNode = repositoriesNode.appendNode("repository")
                    yakclientRepositoryNode.appendNode("id", "yakclient")
                    yakclientRepositoryNode.appendNode("url", "http://maven.yakclient.net/snapshots")
                }

                developers {
                    developer {
                        name.set("Durgan McBroom")
                    }
                }

                licenses {
                    license {
                        name.set("GNU General Public License")
                        url.set("https://opensource.org/licenses/gpl-license")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/yakclient/launchermeta-handler")
                    developerConnection.set("scm:git:ssh://github.com:yakclient/launchermeta-handler.git")
                    url.set("https://github.com/yakclient/launchermeta-handler")
                }
            }
        }
    }
}
