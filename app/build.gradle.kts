plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.fipscan"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.fipscan"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            //isMinifyEnabled = true
            //isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

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
}

configurations.all {
    resolutionStrategy {
        force("org.bouncycastle:bcpkix-jdk18on:1.80")
        force("org.bouncycastle:bcprov-jdk18on:1.80")
        force("org.jetbrains:annotations:23.0.0")
        force("com.intellij:annotations:12.0")
    }
    exclude(group = "com.intellij", module = "annotations")
    exclude(group = "com.kohlschutter.junixsocket", module = "junixsocket-native-common")
    exclude(group = "com.kohlschutter.junixsocket", module = "junixsocket-native-aarch64-MacOSX-clang")
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
    implementation("commons-io:commons-io:2.20.0")

    // Commons Net do obsługi FTP
    implementation("commons-net:commons-net:3.12.0")

    // Apache Commons CSV do operacji na plikach CSV
    implementation("org.apache.commons:commons-csv:1.14.1")

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.4")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.4")

    implementation(libs.annotations)

    implementation(libs.androidx.exifinterface)

    implementation (libs.androidx.core.ktx.v1150)

    implementation(project(":opencv"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
