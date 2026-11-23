import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.android.gms.oss-licenses-plugin")
}

// Підписування релізу: локальний keystore.properties (поза git, див. docs/store/RELEASE_SIGNING.md).
// Якщо файла або будь-якого з ключів немає, release лишається непідписаним, а збірка НЕ падає.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProps.getProperty(it).isNullOrBlank() } &&
    rootProject.file(keystoreProps.getProperty("storeFile", "")).isFile

// URL політики приватності (GitHub Pages з docs/privacy/). Перевизначення: -PprivacyPolicyUrl=...
val privacyPolicyUrl: String = (project.findProperty("privacyPolicyUrl") as String?)
    ?: "https://savoj13.github.io/anti-pdf-/privacy/"

android {
    namespace = "com.savoj.volumpdf"
    compileSdk = 36
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "com.savoj.volumpdf.anti"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // AdMob Application ID у маніфест: debug завжди тестовий, release з admob.properties (див. кореневий build.gradle.kts)
    @Suppress("UNCHECKED_CAST")
    val admob = rootProject.extra["admob"] as Map<String, String>
    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = admob.getValue("testAppId")
        }
        release {
            manifestPlaceholders["admobAppId"] = admob.getValue("releaseAppId")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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

    // Переклади: відсутній чи зайвий ключ у будь-якій мові ламає збірку (і debug lint, і реліз).
    lint {
        error += setOf("MissingTranslation", "ExtraTranslation")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core-pdf"))
    implementation(project(":core-ads"))

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Document Scanner API (ТОП-1)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.mockito:mockito-core:5.15.2")
}

// Перевірка склеєного маніфесту: debug лише тестовий AdMob Application ID, release збігається з конфігурацією.
// Падає при розбіжності. Підключено до check і (після збірки) до assemble/bundleRelease.
@Suppress("UNCHECKED_CAST")
val admobCfg = rootProject.extra["admob"] as Map<String, String>

abstract class VerifyAdmobManifestTask : DefaultTask() {
    @get:InputFile abstract val manifest: RegularFileProperty
    @get:Input abstract val expectedAppId: Property<String>
    @get:Input abstract val variantName: Property<String>
    @get:Input abstract val testPublisher: Property<String>
    @get:Input abstract val mustBeTest: Property<Boolean>

    @TaskAction
    fun verify() {
        val text = manifest.get().asFile.readText()
        val m = Regex("""com\.google\.android\.gms\.ads\.APPLICATION_ID"\s+android:value="([^"]*)"""").find(text)
            ?: throw GradleException("AdMob: у склеєному маніфесті ${variantName.get()} немає APPLICATION_ID")
        val actual = m.groupValues[1]
        if (actual != expectedAppId.get()) {
            throw GradleException("AdMob: Application ID у маніфесті ${variantName.get()} не збігається з очікуваним")
        }
        if (mustBeTest.get() && !actual.startsWith(testPublisher.get())) {
            throw GradleException("AdMob: у ${variantName.get()} знайдено НЕ тестовий Application ID")
        }
        println("AdMob: маніфест ${variantName.get()} OK (${if (actual.startsWith(testPublisher.get())) "тестовий" else "бойовий"} Application ID)")
    }
}

androidComponents {
    onVariants { variant ->
        val isDebug = variant.buildType == "debug"
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val t = tasks.register<VerifyAdmobManifestTask>("verifyAdmob${cap}Manifest") {
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
            expectedAppId.set(if (isDebug) admobCfg.getValue("testAppId") else admobCfg.getValue("releaseAppId"))
            variantName.set(variant.name)
            testPublisher.set(admobCfg.getValue("testPublisher"))
            mustBeTest.set(isDebug)
        }
        if (isDebug) {
            tasks.matching { it.name == "check" }.configureEach { dependsOn(t) }
        } else {
            tasks.matching { it.name == "assemble$cap" || it.name == "bundle$cap" }.configureEach { finalizedBy(t) }
        }
    }
}
