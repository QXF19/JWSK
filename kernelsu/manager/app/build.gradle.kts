@file:Suppress("UnstableApiUsage")

import com.google.protobuf.gradle.id
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
    id("kotlin-parcelize")
}

val androidCompileSdkVersion = rootProject.extra["androidCompileSdkVersion"] as Int
val androidCompileSdkVersionMinor = rootProject.extra["androidCompileSdkVersionMinor"] as Int
val androidCompileNdkVersion = rootProject.extra["androidCompileNdkVersion"] as String
val androidBuildToolsVersion = rootProject.extra["androidBuildToolsVersion"] as String
val androidMinSdkVersion = rootProject.extra["androidMinSdkVersion"] as Int
val androidTargetSdkVersion = rootProject.extra["androidTargetSdkVersion"] as Int
val androidSourceCompatibility = rootProject.extra["androidSourceCompatibility"] as JavaVersion
val androidTargetCompatibility = rootProject.extra["androidTargetCompatibility"] as JavaVersion
val managerVersionCode = rootProject.extra["managerVersionCode"] as Int
val managerVersionName = rootProject.extra["managerVersionName"] as String

val isPrBuild = project.findProperty("IS_PR_BUILD")?.toString()?.toBoolean() ?: false
val defaultManagerPackageName = if (isPrBuild) "me.weishu.kernelsu.pr" else "me.weishu.kernelsu"
val defaultManagerName = if (isPrBuild) "KernelSU PR" else "KernelSU"
val managerPackageName = project.findProperty("KSU_PACKAGE_NAME")?.toString() ?: "cn.jiangwang.jwsk"
val managerName = project.findProperty("KSU_NAME")?.toString() ?: "JWSK"

// Reuse the existing ignored JWSK signing file. Legacy relative KEYSTORE_FILE
// paths are relative to the original manager/ directory, as in signing.gradle.
// CI may instead supply the four KEYSTORE_* variables; use an absolute key path.
val signingProperties = Properties()
val signingPropertiesFile = providers.environmentVariable("JWSK_SIGNING_PROPERTIES")
    .orNull?.let(rootProject::file) ?: rootProject.file("../../signing.properties")
if (signingPropertiesFile.isFile) {
    signingPropertiesFile.inputStream().use { signingProperties.load(it) }
}
fun signingValue(name: String): String? = providers.environmentVariable(name).orNull
    ?.takeIf { it.isNotBlank() } ?: signingProperties.getProperty(name)?.takeIf { it.isNotBlank() }
val signingKeyFile = signingValue("KEYSTORE_FILE")?.let {
    val keyFile = File(it)
    if (keyFile.isAbsolute) keyFile else rootProject.file("../../manager").resolve(it)
}
val signingStorePassword = signingValue("KEYSTORE_PASSWORD")
val signingAlias = signingValue("KEYSTORE_ALIAS")
val signingKeyPassword = signingValue("KEYSTORE_ALIAS_PASSWORD")
val expectedReleaseCertificate = "1f934155f4ffc1aeefb44faa15bdbef277ca4472435ed45e09fa583b9d67a76f"

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Require the existing JWSK release signing identity, without debug fallback."
    doLast {
        check(signingKeyFile?.isFile == true && signingStorePassword != null &&
            signingAlias != null && signingKeyPassword != null) {
            "JWSK release signing credentials are required. Debug signing is not allowed for release."
        }
        val certificate = try {
            val store = KeyStore.getInstance(signingKeyFile, signingStorePassword.toCharArray())
            check(store.isKeyEntry(signingAlias)) { "Release key alias is not a private-key entry." }
            store.getCertificate(signingAlias) ?: error("Release certificate not found.")
        } catch (_: Exception) {
            error("Cannot read the JWSK release certificate. Check local signing credentials.")
        }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        check(fingerprint == expectedReleaseCertificate) {
            "Release certificate differs from published JWSK. Refusing an incompatible signing identity."
        }
        // This is public certificate metadata, never the key, password or keystore bytes.
        logger.lifecycle("JWSK release certificate verified: SHA-256={}, DER bytes={}", fingerprint, certificate.encoded.size)
    }
}

val verifyReleaseBackend = tasks.register("verifyReleaseBackend") {
    group = "verification"
    description = "Refuse a release containing the stock manager identity or unverified daemon artifacts."
    doLast {
        val receiptFile = file("src/main/assets/jwsk_backend.properties")
        check(receiptFile.isFile) {
            "Matching JWSK native backend receipt is missing. Build the native backend validation workflow first."
        }
        val receipt = Properties().apply { receiptFile.inputStream().use { load(it) } }
        check(receipt.getProperty("KSU_PACKAGE_NAME") == managerPackageName && managerPackageName == "cn.jiangwang.jwsk")
        check(receipt.getProperty("KSU_EXPECTED_HASH") == expectedReleaseCertificate)
        check(receipt.getProperty("KSU_EXPECTED_SIZE") == "0x0524")
        check(receipt.getProperty("KSU_PINNED_VERSION") == "32680")
        check(receipt.getProperty("KSU_SOURCE_COMMIT") == "0c0fdb960178fa12d80d6cbcfdcb6ad8be6a865d")
        val stockDaemons = setOf(
            "049491e2b4cc0a6c913d3920cb90b8c94c9e8ca96c0ee92818293304973bfb1b",
            "5324a1e8f2f3c74dc69624c4cd973fb9bc1d5da3614a2f2e694583f73bd13cbd",
        )
        for (abi in listOf("arm64-v8a", "x86_64")) {
            val daemon = file("src/main/jniLibs/$abi/libksud.so")
            check(daemon.isFile) { "Missing JWSK daemon for $abi" }
            val bytes = daemon.readBytes()
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(digest !in stockDaemons) { "Stock KernelSU daemon is not a JWSK release backend: $abi" }
            check(receipt.getProperty("KSUD_SHA256_$abi") == digest) { "Backend receipt does not match $abi" }
            check(String(bytes, Charsets.ISO_8859_1).contains(managerPackageName)) {
                "Daemon does not contain the JWSK manager identity: $abi"
            }
        }
        logger.lifecycle("JWSK native backend identity and daemon artifact hashes verified")
    }
}

tasks.configureEach {
    if (name == "preReleaseBuild" || name == "validateSigningRelease") dependsOn(verifyReleaseSigning)
    if (name == "preReleaseBuild") dependsOn(verifyReleaseBackend)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        ofNonTest().forEach { task ->
            task.builtins {
                id("java") {
                    option("lite")
                }
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

val baseCFlags = listOf(
    "-Wall", "-Qunused-arguments", "-fvisibility=hidden", "-fvisibility-inlines-hidden",
    "-fno-exceptions", "-fno-stack-protector", "-fomit-frame-pointer",
    "-Wno-builtin-macro-redefined", "-Wno-unused-value", "-D__FILE__=__FILE_NAME__"
)
val baseCppFlags = baseCFlags + "-fno-rtti"

android {
    namespace = "me.weishu.kernelsu"

    signingConfigs {
        create("jwskRelease") {
            storeFile = signingKeyFile
            storePassword = signingStorePassword
            keyAlias = signingAlias
            keyPassword = signingKeyPassword
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DCMAKE_CXX_FLAGS_DEBUG=-Og", "-DCMAKE_C_FLAGS_DEBUG=-Og")
                }
            }
        }
        release {
            signingConfig = signingConfigs.getByName("jwskRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            externalNativeBuild {
                cmake {
                    arguments += "-DDEBUG_SYMBOLS_PATH=${layout.buildDirectory.get().asFile.absolutePath}/symbols"
                    arguments += "-DCMAKE_BUILD_TYPE=Release"

                    val releaseFlags = listOf(
                        "-flto", "-ffunction-sections", "-fdata-sections", "-Wl,--gc-sections",
                        "-fno-unwind-tables", "-fno-asynchronous-unwind-tables", "-Wl,--exclude-libs,ALL"
                    )
                    val configFlags = listOf("-Oz", "-DNDEBUG").joinToString(" ")

                    cppFlags += releaseFlags
                    cFlags += releaseFlags

                    arguments += listOf(
                        "-DCMAKE_CXX_FLAGS_RELEASE=$configFlags",
                        "-DCMAKE_C_FLAGS_RELEASE=$configFlags",
                        "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,--icf=all -s -Wl,--hash-style=sysv -Wl,-z,norelro"
                    )
                }
            }
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        resValues = true
        compose = true
        prefab = true
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
            excludes += "lib/*/libandroidx.graphics.path.so"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        generateLocaleConfig = true
    }
    compileSdk {
        version =
            release(androidCompileSdkVersion) {
                minorApiLevel = androidCompileSdkVersionMinor
            }
    }
    buildToolsVersion = androidBuildToolsVersion
    ndkVersion = androidCompileNdkVersion

    defaultConfig {
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = managerVersionCode
        versionName = managerVersionName
        applicationId = managerPackageName

        buildConfigField("boolean", "IS_PR_BUILD", isPrBuild.toString())
        // Backend identity must not be compared with JWSK's independent APK version.
        buildConfigField("int", "KSU_UPSTREAM_VERSION_CODE", "32680")
        resValue("string", "app_name", managerName)

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
                cFlags += baseCFlags + "-std=c2x"
                cppFlags += baseCppFlags + "-std=c++2b"
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) {
        it.packaging.resources.excludes.addAll(listOf("META-INF/**", "kotlin/**", "**.bin"))
    }
}

base {
    archivesName.set(
        "${managerName.replace(" ", "_")}_${managerVersionName}_${managerVersionCode}"
    )
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigationevent.compose)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)

    implementation(libs.dev.rikka.rikkax.parcelablelist)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.task.list.items)

    implementation(libs.androidx.webkit)

    implementation(libs.lsposed.cxx)

    implementation(libs.hiddenapibypass)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.nav)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    implementation(libs.material.kolor)

    implementation(libs.appiconloader)

    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.protobuf.kotlin.lite)

    testImplementation(libs.junit)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}
