import java.util.Properties
import java.net.InetAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Runtime configuration can come from a developer-only local.properties file,
// Gradle properties, or CI environment variables. Release validation below
// fails closed when the backend configuration is absent or unsafe.
fun configuredValue(name: String): String =
    providers.gradleProperty(name).orNull?.trim()?.takeIf(String::isNotEmpty)
        ?: System.getenv(name)?.trim()?.takeIf(String::isNotEmpty)
        ?: localProps.getProperty(name)?.trim().orEmpty()

val supabaseUrl: String = configuredValue("SUPABASE_URL")
val supabaseAnonKey: String = configuredValue("SUPABASE_ANON_KEY")
val googleWebClientId: String = configuredValue("GOOGLE_WEB_CLIENT_ID")
val whatsAppLinkingSetting = configuredValue("WHATSAPP_LINKING_ENABLED")
val whatsAppLinkingEnabled = whatsAppLinkingSetting.equals("true", ignoreCase = true)
val whatsAppBotNumber = configuredValue("WHATSAPP_BOT_NUMBER")
val releasePublicUrls = mapOf(
    "TERMS_URL" to configuredValue("TERMS_URL"),
    "PRIVACY_URL" to configuredValue("PRIVACY_URL"),
    "ACCOUNT_DELETION_URL" to configuredValue("ACCOUNT_DELETION_URL"),
)

fun buildConfigString(value: String): String =
    "\"${value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
    }\""

/** Returns a release-gate problem without ever echoing the configured key. */
fun supabasePublicKeyProblem(value: String): String? {
    if (value.isBlank()) return null

    val normalized = value.lowercase()
    if (normalized.startsWith("sb_secret_") || normalized.startsWith("sb_service_role_")) {
        return "SUPABASE_ANON_KEY is a server-only secret key"
    }
    if (Regex("^sb_publishable_[A-Za-z0-9_-]{16,}${'$'}").matches(value)) return null
    if (normalized.startsWith("sb_")) {
        return "SUPABASE_ANON_KEY is not a valid publishable key"
    }

    val jwtParts = value.split('.')
    if (jwtParts.size != 3) {
        return "SUPABASE_ANON_KEY must be a publishable key or a legacy anon JWT"
    }
    val role = runCatching {
        val payload = jwtParts[1]
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val decoded = String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8)
        Regex("\\\"role\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(decoded)
            ?.groupValues
            ?.get(1)
    }.getOrNull()
    return if (role == "anon") {
        null
    } else {
        "SUPABASE_ANON_KEY legacy JWT does not declare the anon role"
    }
}

fun isValidGoogleWebClientId(value: String): Boolean =
    Regex("^[0-9]+-[A-Za-z0-9_-]+\\.apps\\.googleusercontent\\.com${'$'}").matches(value)

private val reservedReleaseHostSuffixes = setOf(
    "alt",
    "arpa",
    "example",
    "internal",
    "invalid",
    "local",
    "localdomain",
    "localhost",
    "onion",
    "test",
)

private val reservedDocumentationHosts = setOf(
    "example.com",
    "example.net",
    "example.org",
)

/** Parses dotted-decimal IPv4 only; ambiguous octal-like components fail closed. */
fun parseIpv4Literal(host: String): ByteArray? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    val octets = parts.map { part ->
        if (
            part.isEmpty() ||
            part.any { !it.isDigit() } ||
            (part.length > 1 && part.startsWith('0'))
        ) return null
        part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
    }
    return ByteArray(4) { index -> octets[index].toByte() }
}

fun isNonPublicIpv4(address: ByteArray): Boolean {
    require(address.size == 4)
    val first = address[0].toInt() and 0xff
    val second = address[1].toInt() and 0xff
    val third = address[2].toInt() and 0xff
    return when {
        first == 0 -> true // Current network / unspecified.
        first == 10 -> true
        first == 100 && second in 64..127 -> true // Shared carrier-grade NAT.
        first == 127 -> true
        first == 169 && second == 254 -> true
        first == 172 && second in 16..31 -> true
        first == 192 && second == 0 && third == 0 -> true
        first == 192 && second == 0 && third == 2 -> true // TEST-NET-1.
        first == 192 && second == 88 && third == 99 -> true
        first == 192 && second == 168 -> true
        first == 198 && second in 18..19 -> true // Benchmarking.
        first == 198 && second == 51 && third == 100 -> true // TEST-NET-2.
        first == 203 && second == 0 && third == 113 -> true // TEST-NET-3.
        first >= 224 -> true // Multicast, reserved and limited broadcast.
        else -> false
    }
}

fun isNonPublicIp(address: ByteArray): Boolean {
    if (address.size == 4) return isNonPublicIpv4(address)
    if (address.size != 16) return true

    val unsigned = address.map { it.toInt() and 0xff }
    val isUnspecified = unsigned.all { it == 0 }
    val isLoopback = unsigned.take(15).all { it == 0 } && unsigned[15] == 1
    val isUniqueLocal = (unsigned[0] and 0xfe) == 0xfc
    val isLinkLocal = unsigned[0] == 0xfe && (unsigned[1] and 0xc0) == 0x80
    val isMulticast = unsigned[0] == 0xff
    val isDocumentation = unsigned[0] == 0x20 && unsigned[1] == 0x01 &&
        unsigned[2] == 0x0d && unsigned[3] == 0xb8
    val isBenchmarking = unsigned.take(6) == listOf(0x20, 0x01, 0x00, 0x02, 0x00, 0x00)
    val isOrchid = unsigned[0] == 0x20 && unsigned[1] == 0x01 &&
        unsigned[2] == 0x00 && (unsigned[3] and 0xf0) in setOf(0x10, 0x20)
    val isLocalNat64 = unsigned.take(6) == listOf(0x00, 0x64, 0xff, 0x9b, 0x00, 0x01)
    val embeddedIpv4Offset = when {
        unsigned.take(12).all { it == 0 } -> 12
        unsigned.take(10).all { it == 0 } && unsigned[10] == 0xff && unsigned[11] == 0xff -> 12
        else -> null
    }
    val hasNonPublicEmbeddedIpv4 = embeddedIpv4Offset?.let { offset ->
        isNonPublicIpv4(address.copyOfRange(offset, offset + 4))
    } ?: false

    return isUnspecified || isLoopback || isUniqueLocal || isLinkLocal || isMulticast ||
        isDocumentation || isBenchmarking || isOrchid || isLocalNat64 || hasNonPublicEmbeddedIpv4
}

/**
 * Checks only URL syntax and literal/special-use destinations. It deliberately performs no
 * DNS or HTTP request, so public self-hosted Supabase origins remain valid and CI is deterministic.
 */
fun isPublicReleaseHost(rawHost: String): Boolean {
    val host = rawHost
        .removePrefix("[")
        .removeSuffix("]")
        .trimEnd('.')
        .lowercase(Locale.ROOT)
    if (host.isBlank() || '%' in host) return false

    if (':' in host) {
        if (!Regex("^[0-9a-f:.]+${'$'}").matches(host)) return false
        val address = runCatching { InetAddress.getByName(host).address }.getOrNull()
            ?: return false
        return !isNonPublicIp(address)
    }

    if (host.all { it.isDigit() || it == '.' }) {
        val address = parseIpv4Literal(host) ?: return false
        return !isNonPublicIpv4(address)
    }

    if (host.length > 253) return false
    val labels = host.split('.')
    if (labels.size < 2 || labels.any { label ->
            label.isEmpty() ||
                label.length > 63 ||
                !label.first().isLetterOrDigit() ||
                !label.last().isLetterOrDigit() ||
                label.any { !it.isLetterOrDigit() && it != '-' }
        }
    ) return false

    fun isOrIsBelow(suffix: String): Boolean = host == suffix || host.endsWith(".$suffix")
    return reservedReleaseHostSuffixes.none(::isOrIsBelow) &&
        reservedDocumentationHosts.none(::isOrIsBelow)
}

// Release credentials can come from a developer-only local.properties file,
// Gradle properties, or CI environment variables. None of these values belongs
// in version control.
fun releaseCredential(name: String): String? =
    configuredValue(name).takeIf(String::isNotEmpty)

val releaseSigningCredentials = mapOf(
    "RELEASE_KEYSTORE_PATH" to releaseCredential("RELEASE_KEYSTORE_PATH"),
    "RELEASE_KEYSTORE_PASSWORD" to releaseCredential("RELEASE_KEYSTORE_PASSWORD"),
    "RELEASE_KEY_ALIAS" to releaseCredential("RELEASE_KEY_ALIAS"),
    "RELEASE_KEY_PASSWORD" to releaseCredential("RELEASE_KEY_PASSWORD"),
)
val releaseSigningConfigured = releaseSigningCredentials.values.all { !it.isNullOrBlank() }

android {
    namespace = "com.qolve.fluyo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.qolve.fluyo"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "1.0.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", buildConfigString(supabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", buildConfigString(supabaseAnonKey))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", buildConfigString(googleWebClientId))
        buildConfigField("boolean", "WHATSAPP_LINKING_ENABLED", whatsAppLinkingEnabled.toString())
        buildConfigField("String", "WHATSAPP_BOT_NUMBER", buildConfigString(whatsAppBotNumber))
        // Debug builds deliberately have no implicit legal destinations. The
        // release variant overrides these from developer-local configuration.
        buildConfigField("String", "TERMS_URL", buildConfigString(""))
        buildConfigField("String", "PRIVACY_URL", buildConfigString(""))
        buildConfigField("String", "ACCOUNT_DELETION_URL", buildConfigString(""))
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = rootProject.file(releaseSigningCredentials.getValue("RELEASE_KEYSTORE_PATH")!!)
                storePassword = releaseSigningCredentials.getValue("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningCredentials.getValue("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigningCredentials.getValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        val release by getting {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField(
                "String",
                "TERMS_URL",
                buildConfigString(releasePublicUrls.getValue("TERMS_URL")),
            )
            buildConfigField(
                "String",
                "PRIVACY_URL",
                buildConfigString(releasePublicUrls.getValue("PRIVACY_URL")),
            )
            buildConfigField(
                "String",
                "ACCOUNT_DELETION_URL",
                buildConfigString(releasePublicUrls.getValue("ACCOUNT_DELETION_URL")),
            )
            // Bundle native debug symbols (.so libraries from ML Kit, DataStore,
            // androidx.graphics.path) for Play Vitals crash readability. Output goes
            // to app/build/outputs/native-debug-symbols/release/ and must be uploaded
            // separately in Play Console → App bundle explorer → Native debug symbols.
            ndk {
                debugSymbolLevel = "FULL"
            }
        }

        // Explicit escape hatch for local inspection only. Distribution tasks for
        // the real release variant fail closed when signing is incomplete, while
        // this separate variant always produces an unsigned artifact intentionally.
        create("releaseUnsigned") {
            initWith(release)
            signingConfig = null
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties"
            )
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        abortOnError = true
    }
}

// JVM coverage is measured for the debug variant. Generated DI/resource classes
// are excluded; product code (including Compose/ViewModels) remains visible so
// the report does not overstate the project's current test depth.
kover {
    reports {
        variant("debug") {
            filters {
                excludes {
                    classes(
                        "*.BuildConfig",
                        "*.R",
                        "*.R${'$'}*",
                        "*.*_Factory",
                        "*.*_MembersInjector",
                        "*.Dagger*",
                        "*.Hilt_*",
                        "*.*_HiltModules*",
                        "dagger.hilt.internal.aggregatedroot.codegen.*",
                        "hilt_aggregated_deps.*",
                    )
                }
            }
            xml {
                onCheck = true
            }
            html {
                onCheck = false
            }
            log {
                onCheck = true
            }
            verify {
                rule {
                    // The remediated suite measures just over 18% of product-code lines.
                    // Freeze that floor so coverage cannot silently regress while the
                    // still-low UI/integration depth is expanded incrementally.
                    minBound(18)
                }
            }
        }
    }
}

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails when a distributable release lacks signing credentials or required HTTPS URLs."

    doLast {
        fun isAbsolutePublicHttpsUrl(value: String): Boolean =
            value.isNotBlank() &&
                '<' !in value &&
                '>' !in value &&
                runCatching { URI(value) }
                    .getOrNull()
                    ?.let { uri ->
                        uri.scheme.equals("https", ignoreCase = true) &&
                            !uri.host.isNullOrBlank() &&
                            uri.userInfo == null &&
                            isPublicReleaseHost(uri.host)
                    } == true

        fun isSupabaseHttpsOrigin(value: String): Boolean =
            isAbsolutePublicHttpsUrl(value) &&
                runCatching { URI(value) }.getOrNull()?.let { uri ->
                    (uri.path.isNullOrEmpty() || uri.path == "/") &&
                        uri.query == null &&
                        uri.fragment == null
                } == true

        val missingSigning = releaseSigningCredentials
            .filterValues { it.isNullOrBlank() }
            .keys
            .sorted()
        val missingUrls = releasePublicUrls
            .filterValues(String::isBlank)
            .keys
            .sorted()
        val invalidUrls = releasePublicUrls
            .filterValues { value ->
                value.isNotBlank() && !isAbsolutePublicHttpsUrl(value)
            }
            .keys
            .sorted()
        val missingRuntime = buildList {
            if (supabaseUrl.isBlank()) add("SUPABASE_URL")
            if (supabaseAnonKey.isBlank()) add("SUPABASE_ANON_KEY")
            if (googleWebClientId.isBlank()) add("GOOGLE_WEB_CLIENT_ID")
        }
        val invalidSupabaseUrl = supabaseUrl.isNotBlank() && !isSupabaseHttpsOrigin(supabaseUrl)
        val invalidSupabaseKey = supabasePublicKeyProblem(supabaseAnonKey)
        val invalidGoogleClientId = googleWebClientId.isNotBlank() &&
            !isValidGoogleWebClientId(googleWebClientId)
        val invalidWhatsAppSetting = whatsAppLinkingSetting.isNotBlank() &&
            !whatsAppLinkingSetting.equals("true", ignoreCase = true) &&
            !whatsAppLinkingSetting.equals("false", ignoreCase = true)
        val invalidWhatsAppNumber = whatsAppLinkingEnabled &&
            !Regex("^[1-9][0-9]{7,14}$").matches(whatsAppBotNumber)

        val problems = buildList {
            if (missingSigning.isNotEmpty()) {
                add("missing signing values: ${missingSigning.joinToString()}")
            }
            if (missingUrls.isNotEmpty()) {
                add("missing public URLs: ${missingUrls.joinToString()}")
            }
            if (invalidUrls.isNotEmpty()) {
                add(
                    "URLs must be absolute HTTPS destinations on public, non-reserved hosts: " +
                        invalidUrls.joinToString(),
                )
            }
            if (missingRuntime.isNotEmpty()) {
                add("missing backend values: ${missingRuntime.joinToString()}")
            }
            if (invalidSupabaseUrl) {
                add(
                    "SUPABASE_URL must be a public, non-reserved HTTPS origin without " +
                        "credentials, path, query or fragment",
                )
            }
            if (invalidSupabaseKey != null) {
                add(invalidSupabaseKey)
            }
            if (invalidGoogleClientId) {
                add("GOOGLE_WEB_CLIENT_ID must be a web OAuth client ID")
            }
            if (invalidWhatsAppSetting) {
                add("WHATSAPP_LINKING_ENABLED must be true or false")
            }
            if (invalidWhatsAppNumber) {
                add("WHATSAPP_BOT_NUMBER must be E.164 digits when WhatsApp linking is enabled")
            }
        }
        check(problems.isEmpty()) {
            "Release distribution configuration is incomplete (${problems.joinToString("; ")}). " +
                "Configure backend values, URLs and signing values in local.properties, Gradle properties, " +
                "or environment variables. For an intentionally unsigned local bundle, run " +
                ":app:bundleReleaseUnsigned."
        }

        val keystore = rootProject.file(releaseSigningCredentials.getValue("RELEASE_KEYSTORE_PATH")!!)
        check(keystore.isFile) {
            "Release keystore does not exist or is not a file: ${keystore.absolutePath}"
        }
    }
}

// Protect every directly invokable packaging/signing entry point for the real
// release variant. Debug and releaseUnsigned tasks remain usable without secrets.
tasks.configureEach {
    val protectedPrefixes = listOf(
        "assembleRelease",
        "bundleRelease",
        "packageRelease",
        "signRelease",
        "installRelease",
        "publishRelease",
    )
    if (protectedPrefixes.any(name::startsWith) && !name.contains("Unsigned")) {
        dependsOn(validateReleaseSigning)
    }
}

dependencies {
    // java.time on API 24
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    // Compose BOM + UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // AndroidX core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.android.compiler)

    // Coroutines + Serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.compose.auth)
    implementation(libs.ktor.client.okhttp)

    // DataStore + Coil
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)

    // ML Kit on-device OCR + coroutine adapter for Google Tasks
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.activity.ktx)

    // WorkManager + Hilt integration for scheduled nudges
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Credential Manager + Google ID (modern Google Sign-In)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
