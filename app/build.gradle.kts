plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.healthetileplugin"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.healthetileplugin"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(Dependencies.coreKtx)
    implementation(Dependencies.lifeCycleKtx)
    implementation(Dependencies.activityKtx)
    implementation(platform(Dependencies.composeKtx))
    implementation(Dependencies.uiKtx)
    implementation(Dependencies.uiGraphicsKtx)
    implementation(Dependencies.uiToolingKtx)
    implementation(Dependencies.materialKtx)
    implementation(Dependencies.hiltAndroid)
    implementation(Dependencies.hiltNavigationCompose)
    implementation(Dependencies.interceptor)
    implementation(Dependencies.retrofit)
    implementation(Dependencies.okhttp)
    implementation(Dependencies.moshi)
    implementation(Dependencies.moshiConvertor)
    implementation(Dependencies.coroutinesCore)
    implementation(Dependencies.coroutinesAndroid)
    implementation(Dependencies.coil)
    implementation("com.google.accompanist:accompanist-permissions:0.21.1-beta")
    implementation(project(":ble"))
    implementation(libs.androidx.runtime.livedata)

}


//kapt {
//    correctErrorTypes = true
//}