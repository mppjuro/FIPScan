plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

val targetCompatibility by extra(JavaVersion.VERSION_23)

buildscript {
    repositories {
        google()
    }
    dependencies {

        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.9.4")
    }
}