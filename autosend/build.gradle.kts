import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Отдельное автономное приложение: служба специальных возможностей,
// которая нажимает «Отправить» в мессенджере по команде CRM.

// Ключ подписи берётся из локального autosend/keystore.properties (не хранится в git).
// Разрешение управления службой имеет protectionLevel=signature, поэтому CRM
// должна быть подписана тем же ключом. Без файла используется debug-ключ.
val keystoreProps = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.rykov.autosend"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rykov.autosend"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    // Внешних зависимостей нет: только платформа Android и стандартная библиотека Kotlin.
    testImplementation("junit:junit:4.13.2")
}
