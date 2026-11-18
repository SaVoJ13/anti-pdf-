plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.savoj.volumpdf.core.ads"
    compileSdk = 36
    compileSdkExtension = 19

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // debug завжди тестові ID Google; release бере ID з rootProject.extra["admob"] (admob.properties). Див. кореневий build.gradle.kts.
    @Suppress("UNCHECKED_CAST")
    val admob = rootProject.extra["admob"] as Map<String, String>
    buildTypes {
        debug {
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"${admob.getValue("testBanner")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"${admob.getValue("testInterstitial")}\"")
            buildConfigField("boolean", "ADMOB_USING_TEST_IDS", "true")
        }
        release {
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"${admob.getValue("releaseBanner")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"${admob.getValue("releaseInterstitial")}\"")
            buildConfigField("boolean", "ADMOB_USING_TEST_IDS", admob.getValue("releaseIsTest"))
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api("com.google.android.gms:play-services-ads:25.5.0")
    api("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
