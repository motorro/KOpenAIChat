plugins {
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.org.jetbrains.kotlin.jvm) apply false
}
