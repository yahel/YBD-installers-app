#!/usr/bin/env bash
set -euo pipefail

# Run this from your project root (the folder with settings.gradle.kts)
# Usage: bash patch_installerapp.sh

echo "==> Ensuring AndroidX flags"
cat > gradle.properties <<'EOF'
android.useAndroidX=true
android.enableJetifier=true
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
EOF

echo "==> Writing app/build.gradle.kts (known-good)"
cat > app/build.gradle.kts <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.celerate.installer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.celerate.installer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { isMinifyEnabled = false }
    }

    // Make Java & Kotlin both target JVM 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// Extra safety: Kotlin toolchain = 17
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
EOF

echo "==> Patching NetworkStatusViewModel.kt with OkHttp Dns fix"
mkdir -p app/src/main/java/com/celerate/installer/viewmodel
cat > app/src/main/java/com/celerate/installer/viewmodel/NetworkStatusViewModel.kt <<'EOF'
package com.celerate.installer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.celerate.installer.model.ConnectionStatus
import com.celerate.installer.orchestrator.ConnectivityOrchestrator
import com.celerate.installer.orchestrator.ProbeResult
import com.celerate.installer.orchestrator.probeHttp
import com.celerate.installer.orchestrator.probeTcp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

/**
 * NetworkStatusViewModel
 * ----------------------
 * Drives the four status indicators required for field visibility:
 *  • Controller (internet via cellular)
 *  • Installer-AP (AGLR Wi-Fi presence)
 *  • UBNT-Radio (via AGLR)
 *  • Mikrotik (via AGLR)
 *
 * It owns the orchestrator, periodically probes endpoints, and exposes a
 * `StateFlow<ConnectionStatus>` for each indicator to the UI.
 */
class NetworkStatusViewModel(app: Application) : AndroidViewModel(app) {
    private val orchestrator = ConnectivityOrchestrator(app)

    private val _controller = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Unknown)
    val controller: StateFlow<ConnectionStatus> = _controller.asStateFlow()

    private val _aglr = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Unknown)
    val aglr: StateFlow<ConnectionStatus> = _aglr.asStateFlow()

    private val _ubnt = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Unknown)
    val ubnt: StateFlow<ConnectionStatus> = _ubnt.asStateFlow()

    private val _mkt = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Unknown)
    val mkt: StateFlow<ConnectionStatus> = _mkt.asStateFlow()

    // --- Configuration (can be surfaced as editable settings in the UI) ---
    var controllerBaseUrl: String = "https://staging.controller.example" // cloud base URL
    var aglrSsid: String = "AGLR-Installer"                               // Installer-AP SSID
    var aglrPass: String? = null                                           // WPA2 pass if any
    var ubntIp: String = "192.168.1.20"                                   // UBNT default
    var mktIp: String = "192.168.88.1"                                    // Mikrotik default

    private var probeJob: Job? = null

    /** Initiate/hold AGLR Wi-Fi session. Safe to call multiple times. */
    fun connectAglr() {
        viewModelScope.launch {
            _aglr.value = ConnectionStatus.Connecting
            orchestrator.connectToAglr(aglrSsid, aglrPass).collect { net ->
                _aglr.value = if (net != null) ConnectionStatus.Connected() else ConnectionStatus.Disconnected()
            }
        }
    }

    /** Request/hold a cellular network (internet). */
    fun requestCellular() {
        viewModelScope.launch { orchestrator.requestCellular().collect { /* handled via currentHandles() */ } }
    }

    /**
     * Begin periodic probing of all endpoints. Each tick:
     *  1) Updates Installer-AP status from `aglrNetwork` presence
     *  2) Probes Controller over **cellular**
     *  3) Probes UBNT & Mikrotik via **AGLR** using TCP (SSH/HTTP/RouterOS)
     */
    fun startProbing(intervalMs: Long = 3000) {
        if (probeJob

