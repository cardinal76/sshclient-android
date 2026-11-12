plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firma fissa: la stessa chiave ovunque si compili, cosi' un APK costruito da
// GitHub Actions aggiorna quello costruito altrove senza disinstallare (e
// disinstallare cancella le chiavi SSH). Keystore e password arrivano
// dall'ambiente e non stanno nel repository: vedi «Firma» nel README.
// Una variabile vuota vale come assente: e' cosi' che GitHub passa un secret
// che non c'e'.
fun ambiente(nome: String): String? =
    providers.environmentVariable(nome).orNull?.takeIf { it.isNotBlank() }

val firmaKeystore = ambiente("FIRMA_KEYSTORE")
val firmaPassword = ambiente("FIRMA_PASSWORD")
val firmaAlias = ambiente("FIRMA_ALIAS")

if (firmaKeystore == null) {
    logger.warn(
        "ATTENZIONE: FIRMA_KEYSTORE non impostata, l'APK di debug esce con la firma " +
            "di debug di questa macchina e non aggiorna un'app firmata con la chiave " +
            "fissa (vedi «Firma» nel README)."
    )
} else {
    // Meta' configurazione e' un errore, non un motivo per ripiegare in
    // silenzio sulla firma di debug: l'APK uscirebbe con la chiave sbagliata.
    if (firmaPassword == null || firmaAlias == null) {
        throw GradleException(
            "FIRMA_KEYSTORE e' impostata ma manca FIRMA_PASSWORD o FIRMA_ALIAS."
        )
    }
    if (!file(firmaKeystore).isFile) {
        throw GradleException("FIRMA_KEYSTORE indica un file che non esiste: $firmaKeystore")
    }
}

android {
    namespace = "it.cardinal.sshclient"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "it.cardinal.sshclient"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (firmaKeystore != null) {
            create("fissa") {
                storeFile = file(firmaKeystore)
                storePassword = firmaPassword
                keyAlias = firmaAlias
                // In un PKCS12 la chiave ha la stessa password del keystore.
                keyPassword = firmaPassword
            }
        }
    }

    buildTypes {
        // Senza keystore debug resta con la firma di debug predefinita e
        // release esce non firmato, come prima.
        val fissa = signingConfigs.findByName("fissa")
        debug {
            if (fissa != null) signingConfig = fissa
        }
        release {
            if (fissa != null) signingConfig = fissa
            // sshj e BouncyCastle caricano le classi per nome: senza regole
            // R8 scritte apposta la riduzione le toglierebbe.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("com.hierynomus:sshj:0.41.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.connectbot:termlib:0.3.14")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.sshd:sshd-core:2.20.1")
}
