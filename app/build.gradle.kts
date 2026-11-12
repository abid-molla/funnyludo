plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.funny.ludo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.funny.ludo"
        minSdk = 23
        targetSdk = 34
        versionCode = 7
        versionName = "3.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Ads (banner, full-screen, rewarded)
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    // Real-money purchases of coins / diamonds
    implementation("com.android.billingclient:billing:7.1.1")
    // Google Play Games sign-in, achievements, leaderboard
    implementation("com.google.android.gms:play-services-games-v2:20.1.2")
}
