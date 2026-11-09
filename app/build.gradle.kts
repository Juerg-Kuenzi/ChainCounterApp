import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Fester Signaturschluessel, damit eine neue APK die installierte App ersetzt.
// Quelle: Umgebungsvariablen (Workflow) oder keystore.properties (lokal, nie einchecken).
// Fehlt beides, gilt der Debug-Schluessel des jeweiligen Rechners.
val localSigning = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: localSigning.getProperty(property)

val signingKeystore = signingValue("SIGNING_KEYSTORE_FILE", "storeFile")
val signingPassword = signingValue("SIGNING_PASSWORD", "password")

android {
    namespace = "ch.sy_isme.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ch.sy_isme.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "2.0.0"
    }

    val fixedSigning = if (signingKeystore != null && signingPassword != null) {
        signingConfigs.create("fixed") {
            storeFile = rootProject.file(signingKeystore)
            storeType = "PKCS12"
            storePassword = signingPassword
            keyAlias = "chaincounter"
            keyPassword = signingPassword
        }
    } else {
        null
    }

    buildTypes {
        getByName("debug") {
            if (fixedSigning != null) signingConfig = fixedSigning
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
    testImplementation("junit:junit:4.13.2")
}
