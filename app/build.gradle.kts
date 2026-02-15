plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp") version "2.3.4"
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.android)
    id ("androidx.navigation.safeargs.kotlin")
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
        multiDexEnabled = true
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
            isMinifyEnabled = false
            isShrinkResources = false
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

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-storage")
    implementation("com.github.mppjuro:tabula-java-android:7db7d44809")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0") {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcutil-jdk15to18")
        exclude(group = "org.bouncycastle", module = "bcpkix-jdk15to18")
        exclude(group = "com.intellij", module = "annotations")
    }
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("commons-io:commons-io:2.21.0")
    implementation("commons-net:commons-net:3.12.0")
    implementation("org.apache.commons:commons-csv:1.14.1")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation(libs.gson)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.core.ktx)
    ksp(libs.androidx.room.compiler)
    implementation("com.google.zxing:core:3.5.4")
    implementation(libs.androidx.exifinterface)
    implementation(project(":opencv"))
    implementation(libs.annotations)
    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.14.9")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.runner)
}