#!/bin/zsh
set -e

PROJ=~/work/YBD-installers-app
REPO_URL=https://github.com/yahel/YBD-installers-app.git

mkdir -p "$PROJ"/{app/src/main/java/com/celerate/installer/{model,orchestrator,viewmodel,ui},app/src/main/res/{values,drawable,mipmap-anydpi-v26},.github/workflows}
cd "$PROJ"

cat > gradle.properties <<'EOF'
android.useAndroidX=true
android.enableJetifier=true
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
EOF

cat > settings.gradle.kts <<'EOF'
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "InstallerApp"
include(":app")
EOF

cat > build.gradle.kts <<'EOF'
plugins {
    id("com.android.application") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
EOF

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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { jvmToolchain(17) }
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

cat > app/proguard-rules.pro <<'EOF'
# empty
EOF

cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <application
        android:allowBackup="true"
        android:label="InstallerApp"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

cat > app/src/main/res/values/themes.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Material3.DayNight.NoActionBar" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
EOF

cat > app/src/main/res/values/colors.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#0F766E</color>
</resources>
EOF

cat > app/src/main/res/drawable/ic_launcher_foreground.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFF" android:pathData="M54,20a34,34 0,1 0,0,68a34,34 0,1 0,0,-68z"/>
</vector>
EOF

cat > app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
EOF

cat > app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
EOF

cat > app/src/main/java/com/celerate/installer/model/ConnectionStatus.kt <<'EOF'
package com.celerate.installer.model
sealed interface ConnectionStatus {
    data object Unknown : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Connected(val latencyMs: Long? = null) : ConnectionStatus
    data class Disconnected(val reason: String? = null) : ConnectionStatus
}
EOF

cat > app/src/main/java/com/celerate/installer/orchestrator/ConnectivityOrchestrator.kt <<'EOF'
package com.celerate.installer.orchestrator
import android.net.*
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
class ConnectivityOrchestrator(private val context: android.content.Context) {
    data class Handles(val aglr: Network?, val cellular: Network?)
    private val cm: ConnectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private var aglrCallback: ConnectivityManager.NetworkCallback? = null
    private var cellCallback: ConnectivityManager.NetworkCallback? = null
    private val _aglrNetwork = MutableStateFlow<Network?>(null)
    val aglrNetwork: StateFlow<Network?> = _aglrNetwork.asStateFlow()
    private val _cellNetwork = MutableStateFlow<Network?>(null)
    val cellNetwork: StateFlow<Network?> = _cellNetwork.asStateFlow()
    fun connectToAglr(ssid: String, passphrase: String?): Flow<Network?> = callbackFlow {
        val specBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (!passphrase.isNullOrBlank()) specBuilder.setWpa2Passphrase(passphrase)
        val spec = specBuilder.build()
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(spec)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _aglrNetwork.value = network; trySend(network) }
            override fun onLost(network: Network) { if (_aglrNetwork.value == network) _aglrNetwork.value = null; trySend(null) }
        }
        aglrCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        aglrCallback = cb
        cm.requestNetwork(req, cb)
        awaitClose {
            runCatching { cm.unregisterNetworkCallback(cb) }
            if (aglrCallback == cb) aglrCallback = null
            _aglrNetwork.value = null
        }
    }
    fun requestCellular(): Flow<Network?> = callbackFlow {
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _cellNetwork.value = network; trySend(network) }
            override fun onLost(network: Network) { if (_cellNetwork.value == network) _cellNetwork.value = null; trySend(null) }
        }
        cellCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        cellCallback = cb
        cm.requestNetwork(req, cb)
        awaitClose {
            runCatching { cm.unregisterNetworkCallback(cb) }
            if (cellCallback == cb) cellCallback = null
            _cellNetwork.value = null
        }
    }
    fun currentHandles(): Handles = Handles(aglr = _aglrNetwork.value, cellular = _cellNetwork.value)
}
EOF

cat > app/src/main/java/com/celerate/installer/orchestrator/Probers.kt <<'EOF'
package com.celerate.installer.orchestrator
import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import javax.net.SocketFactory
sealed class ProbeResult { data class Up(val latencyMs: Long) : ProbeResult(); data class Down(val reason: String) : ProbeResult() }
suspend fun probeHttp(url: HttpUrl, client: OkHttpClient): ProbeResult = withContext(Dispatchers.IO) {
    val start = System.nanoTime()
    val req = Request.Builder().url(url).head().build()
    return@withContext try {
        client.newCall(req).execute().use { resp ->
            val tookMs = (System.nanoTime() - start) / 1_000_000
            if (resp.isSuccessful || resp.code in 200..499) ProbeResult.Up(tookMs) else ProbeResult.Down("HTTP ${'$'}{resp.code}")
        }
    } catch (e: Exception) { ProbeResult.Down(e.message ?: "error") }
}
suspend fun probeTcp(host: String, port: Int, network: Network): ProbeResult = withContext(Dispatchers.IO) {
    val sf: SocketFactory = network.socketFactory
    val start = System.nanoTime()
    return@withContext try {
        sf.createSocket().use { s -> s.connect(InetSocketAddress(host, port), 1500) }
        ProbeResult.Up((System.nanoTime() - start) / 1_000_000)
    } catch (e: Exception) { ProbeResult.Down(e.message ?: "connect error") }
}
EOF

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
    var controllerBaseUrl: String = "https://staging.controller.example"
    var aglrSsid: String = "AGLR-Installer"
    var aglrPass: String? = null
    var ubntIp: String = "192.168.1.20"
    var mktIp: String = "192.168.88.1"
    private var probeJob: Job? = null
    fun connectAglr() {
        viewModelScope.launch {
            _aglr.value = ConnectionStatus.Connecting
            orchestrator.connectToAglr(aglrSsid, aglrPass).collect { net ->
                _aglr.value = if (net != null) ConnectionStatus.Connected() else ConnectionStatus.Disconnected()
            }
        }
    }
    fun requestCellular() {
        viewModelScope.launch { orchestrator.requestCellular().collect { } }
    }
    fun startProbing(intervalMs: Long = 3000) {
        if (probeJob != null) return
        requestCellular()
        if (_aglr.value !is ConnectionStatus.Connected) connectAglr()
        probeJob = viewModelScope.launch {
            val cloudClient = OkHttpClient.Builder()
                .connectTimeout(1500, TimeUnit.MILLISECONDS)
                .readTimeout(1500, TimeUnit.MILLISECONDS)
                .build()
            while (true) {
                val handles = orchestrator.currentHandles()
                _aglr.value = if (handles.aglr != null) ConnectionStatus.Connected() else ConnectionStatus.Disconnected("not connected")
                _controller.value = ConnectionStatus.Connecting
                val ctlUrl = runCatching { controllerBaseUrl.toHttpUrl() }.getOrNull()
                if (ctlUrl != null && handles.cellular != null) {
                    val pinned = cloudClient.newBuilder()
                        .socketFactory(handles.cellular.socketFactory)
                        .dns(Dns { hostname -> handles.cellular.getAllByName(hostname).toList() })
                        .build()
                    when (val r = probeHttp(ctlUrl, pinned)) {
                        is ProbeResult.Up -> _controller.value = ConnectionStatus.Connected(r.latencyMs)
                        is ProbeResult.Down -> _controller.value = ConnectionStatus.Disconnected(r.reason)
                    }
                } else {
                    _controller.value = ConnectionStatus.Disconnected("no cellular or bad URL")
                }
                if (handles.aglr != null) {
                    val r = probeTcp(ubntIp, 22, handles.aglr)
                    _ubnt.value = when (r) {
                        is ProbeResult.Up -> ConnectionStatus.Connected(r.latencyMs)
                        is ProbeResult.Down -> {
                            val r2 = probeTcp(ubntIp, 80, handles.aglr)
                            when (r2) {
                                is ProbeResult.Up -> ConnectionStatus.Connected(r2.latencyMs)
                                is ProbeResult.Down -> ConnectionStatus.Disconnected(r2.reason)
                            }
                        }
                    }
                } else {
                    _ubnt.value = ConnectionStatus.Disconnected("no AGLR")
                }
                if (handles.aglr != null) {
                    val r = probeTcp(mktIp, 22, handles.aglr)
                    _mkt.value = when (r) {
                        is ProbeResult.Up -> ConnectionStatus.Connected(r.latencyMs)
                        is ProbeResult.Down -> {
                            val r2 = probeTcp(mktIp, 8728, handles.aglr)
                            when (r2) {
                                is ProbeResult.Up -> ConnectionStatus.Connected(r2.latencyMs)
                                is ProbeResult.Down -> ConnectionStatus.Disconnected(r2.reason)
                            }
                        }
                    }
                } else {
                    _mkt.value = ConnectionStatus.Disconnected("no AGLR")
                }
                kotlinx.coroutines.delay(intervalMs)
            }
        }
    }
    fun stopProbing() { probeJob?.cancel(); probeJob = null }
}
EOF

cat > app/src/main/java/com/celerate/installer/ui/StatusIndicator.kt <<'EOF'
package com.celerate.installer.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celerate.installer.model.ConnectionStatus
@Composable
fun StatusPill(label: String, status: ConnectionStatus, modifier: Modifier = Modifier) {
    val (color, text) = when (status) {
        is ConnectionStatus.Connected -> Pair(MaterialTheme.colorScheme.tertiary, status.latencyMs?.let { "$it ms" } ?: "UP")
        is ConnectionStatus.Connecting -> Pair(MaterialTheme.colorScheme.secondary, "…")
        is ConnectionStatus.Disconnected -> Pair(MaterialTheme.colorScheme.error, "DOWN")
        else -> Pair(MaterialTheme.colorScheme.outline, "-")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
EOF

cat > app/src/main/java/com/celerate/installer/MainActivity.kt <<'EOF'
package com.celerate.installer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celerate.installer.ui.StatusPill
import com.celerate.installer.viewmodel.NetworkStatusViewModel
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: NetworkStatusViewModel = viewModel()) {
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Connectivity Orchestrator – Demo") }) }) { padding ->
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = vm.controllerBaseUrl, onValueChange = { vm.controllerBaseUrl = it }, label = { Text("Controller Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = vm.aglrSsid, onValueChange = { vm.aglrSsid = it }, label = { Text("AGLR SSID") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = vm.aglrPass ?: "", onValueChange = { vm.aglrPass = it }, label = { Text("AGLR Pass (opt)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = vm.ubntIp, onValueChange = { vm.ubntIp = it }, label = { Text("UBNT IP") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = vm.mktIp, onValueChange = { vm.mktIp = it }, label = { Text("Mikrotik IP") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.connectAglr() }) { Text("Connect AGLR") }
                    Button(onClick = { vm.requestCellular() }) { Text("Request Cellular") }
                    Button(onClick = { vm.startProbing() }) { Text("Start Probing") }
                    Button(onClick = { vm.stopProbing() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Stop") }
                }
                Divider()
                val ctl by vm.controller.collectAsState()
                val aglr by vm.aglr.collectAsState()
                val ubnt by vm.ubnt.collectAsState()
                val mkt by vm.mkt.collectAsState()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusPill("Controller", ctl)
                    StatusPill("Installer-AP", aglr)
                    StatusPill("UBNT-Radio", ubnt)
                    StatusPill("Mikrotik", mkt)
                }
            }
        }
    }
}
EOF

# ensure Gradle wrapper (8.7)
if [ ! -f gradlew ]; then
  if command -v gradle >/dev/null 2>&1; then
    gradle wrapper --gradle-version 8.7
  else
    echo "Gradle not found; install with: brew install gradle, then rerun this script." >&2
    exit 1
  fi
fi
chmod +x gradlew

# CI workflow
cat > .github/workflows/android-ci.yml <<'EOF'
name: Android CI
on:
  push: { branches: [ main ] }
  pull_request: {}
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - name: Install SDK
        run: |
          sdkmanager --install "platform-tools" "platforms;android-34" "build-tools;34.0.2"
          yes | sdkmanager --licenses
      - name: Build
        shell: bash
        run: |
          set -o pipefail
          chmod +x gradlew
          ./gradlew clean :app:assembleDebug --no-daemon --stacktrace 2>&1 | tee gradle-build.log
      - name: Upload logs (always)
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: build-logs
          path: gradle-build.log
      - name: Upload outputs (always)
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: build-outputs
          path: app/build/outputs/**
EOF

git init
git add -A
git commit -m "seed: installer app skeleton + CI" || true
git branch -M main
git remote remove origin 2>/dev/null || true
git remote add origin "$REPO_URL"
git push -u origin main

echo
echo "Pushed to $REPO_URL"
echo "Latest run:"
gh run list -R yahel/YBD-installers-app --branch main --limit 1
