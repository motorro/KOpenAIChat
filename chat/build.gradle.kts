import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin.dokka)
    id("maven-publish")
    id("signing")
}

group = rootProject.group
version = rootProject.version

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)
            api(project.dependencies.platform(libs.openai.bom))
            api(libs.openai.client)
            api(libs.kotlin.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.okhttp)
        }
    }
}

android {
    namespace = "com.motorro.kopenaichat"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}

val libId = "kopenaichat"
val libName = "kopenaichat"
val libDesc = "Multiplatform OpenAI chat"
val projectUrl: String = "https://github.com/motorro/KOpenAIChat"
val projectScm: String = "https://github.com/motorro/KOpenAIChat.git"
val ossrhUsername: String? by rootProject.extra
val ossrhPassword: String? by rootProject.extra
val developerId: String by rootProject.extra
val developerName: String by rootProject.extra
val developerEmail: String by rootProject.extra
val signingKey: String? by rootProject.extra
val signingPassword: String? by rootProject.extra

val javadocJar by tasks.creating(Jar::class) {
    group = "documentation"
    archiveClassifier.set("javadoc")
    from(tasks.dokkaHtml)
}

publishing {
    repositories {
        maven {
            name = "sonatype"
            url = URI("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = ossrhUsername
                password = ossrhPassword
            }
        }
    }

    publications.withType<MavenPublication> {
        artifact(javadocJar)
        pom {
            name.set(libName)
            description.set(libDesc)
            url.set(projectUrl)
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://apache.org/licenses/LICENSE-2.0")
                }
            }
            developers {
                developer {
                    id.set(developerId)
                    name.set(developerName)
                    email.set(developerEmail)
                }
            }
            scm {
                connection.set(projectScm)
                developerConnection.set(projectScm)
                url.set(projectUrl)
            }
        }
    }
}

val isSnapshot = rootProject.hasProperty("isSnapshot") && true == rootProject.properties["isSnapshot"]

signing {
    isRequired = isSnapshot.not()
    useInMemoryPgpKeys(signingKey, signingPassword)
    sign(publishing.publications)
}

val signingTasks = tasks.withType<Sign>()
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(signingTasks)
}