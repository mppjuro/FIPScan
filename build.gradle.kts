plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.0" apply false
    alias(libs.plugins.kotlin.android) apply false
}

val targetCompatibility by extra(JavaVersion.VERSION_21)

buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.9.6")
    }
}