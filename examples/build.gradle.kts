@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    application
    alias(libs.plugins.org.jetbrains.kotlin.jvm)
}
repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.motorro.kopenaichat.examples.ChatKt")
}

dependencies {
    implementation(project(":shared"))
}