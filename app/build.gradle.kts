import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.smartkeyboard.pro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smartkeyboard.pro"
        minSdk = 26
        targetSdk = 36
        // versionCode must keep increasing so the APK installs over the previous
        // release (and so the in-app updater accepts it). 1.4.9 shipped as 149.
        versionCode = 150
        versionName = "1.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    signingConfigs {
        create("release") {
            // Signing secrets are NEVER hardcoded or committed. Provide them through
            // environment variables or an untracked `keystore.properties` file:
            //   STORE_FILE, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
            //
            // The keystore itself must live outside this repository, for example:
            //   STORE_FILE=$HOME/secure-keys/smartkeyboardpro/my-upload-key.jks
            val propertiesFile = rootProject.file("keystore.properties")
            val properties = Properties().apply {
                if (propertiesFile.exists()) {
                    propertiesFile.inputStream().use { load(it) }
                }
            }

            fun readSecret(envKey: String, propertyKey: String): String? =
                System.getenv(envKey) ?: properties.getProperty(propertyKey)

            val keystoreFile = rootProject.file(readSecret("STORE_FILE", "storeFile") ?: "my-upload-key.jks")
            val resolvedStorePassword = readSecret("STORE_PASSWORD", "storePassword")
            val resolvedKeyAlias = readSecret("KEY_ALIAS", "keyAlias")
            val resolvedKeyPassword = readSecret("KEY_PASSWORD", "keyPassword")

            if (keystoreFile.exists() &&
                resolvedStorePassword != null &&
                resolvedKeyAlias != null &&
                resolvedKeyPassword != null
            ) {
                storeFile = keystoreFile
                storePassword = resolvedStorePassword
                keyAlias = resolvedKeyAlias
                keyPassword = resolvedKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            } else {
                logger.lifecycle(
                    "Release signing disabled: set STORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD " +
                        "(or create keystore.properties) and provide a keystore at ${keystoreFile.path}."
                )
            }
        }
    }

    buildTypes {
        release {
            // Keep R8 enabled: the released APK is distributed outside Play and is
            // downloaded/installed by the in-app updater.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSign = signingConfigs.getByName("release")
            if (releaseSign.storeFile != null && releaseSign.storeFile!!.exists()) {
                signingConfig = releaseSign
            }
        }
        debug {
            // Debug builds are signed with the default debug keystore only. Reusing the
            // production upload key here would let any debug build masquerade as a
            // trusted release of the same package name.
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        jvmToolchain(21)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
