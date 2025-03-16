plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.fipscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fipscan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    // Aby wykluczyć zduplikowane pliki w dependencies
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/DEPENDENCIES",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "licenses/extreme.indiana.edu.license.TXT",
                "licenses/thoughtworks.TXT",
                "licenses/javolution.license.TXT",
                "META-INF/org/jetbrains/annotations/annotations.version",
                "META-INF/com/intellij/annotations/annotations.version"
            )
        }
    }
    viewBinding {
        enable = true
    }
}

configurations.all {
    resolutionStrategy {
        force("org.bouncycastle:bcpkix-jdk18on:1.80")
        force("org.bouncycastle:bcprov-jdk18on:1.80")
        force("org.jetbrains:annotations:23.0.0")
        force("com.intellij:annotations:12.0")
    }
    exclude(group = "com.intellij", module = "annotations")
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.gson)

    // Tabula dla Androida (do ekstrakcji tabel)
    implementation("com.github.mppjuro:tabula-java-android:7db7d44809")

    // Poprawiona wersja PDFBox-Android
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcutil-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcpkix-jdk15to18")
        exclude(group = "com.intellij", module = "annotations")
    }

    // Commons IO dla operacji na plikach
    implementation("commons-io:commons-io:2.11.0")

    // Commons Net do obsługi FTP
    implementation("commons-net:commons-net:3.6")

    // Apache Commons CSV do operacji na plikach CSV
    implementation("org.apache.commons:commons-csv:1.9.0")

    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.9")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.9")

    implementation("org.jetbrains:annotations:23.0.0")

    implementation("androidx.exifinterface:exifinterface:1.3.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}