package com.celerate.installer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.celerate.installer.ui.StatusPill
import com.celerate.installer.viewmodel.NetworkStatusViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Temporary crash trap to get clean stacks in Logcat
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            Log.e("AppCrash", "Uncaught exception in thread ${t.name}", e)
        }
        setContent { App() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val vm: NetworkStatusViewModel = viewModel()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var controller by rememberSaveable { mutableStateOf(vm.controllerBaseUrl) }
    var ssid by rememberSaveable { mutableStateOf(vm.aglrSsid) }
    var pass by rememberSaveable { mutableStateOf(vm.aglrPass ?: "") }
    var ubntIp by rememberSaveable { mutableStateOf(vm.ubntIp) }
    var mktIp by rememberSaveable { mutableStateOf(vm.mktIp) }

    var showSsidPicker by remember { mutableStateOf(false) }
    var scannedSsids by remember { mutableStateOf(listOf<String>()) }

    val wifiPerms = remember {
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        val granted = wifiPerms.all { res[it] == true }
        if (granted) {
            Log.d("MainActivity", "Permission granted; attempting connectAglr()")
            try {
                vm.connectAglr()
            } catch (t: Throwable) {
                Log.e("MainActivity", "connectAglr() after permission grant crashed", t)
                scope.launch { snackbar.showSnackbar("Connect failed: ${t.message ?: "unknown error"}") }
            }
        } else {
            scope.launch { snackbar.showSnackbar("Wi-Fi permission is required") }
        }
    }

    fun hasAll(perms: Array<String>) =
        perms.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    fun isLocationEnabled(): Boolean = try {
        (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled
    } catch (_: Exception) { true }

    fun scanSsids() {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Try an active scan; on many devices this is throttled but still works in foreground with CHANGE_WIFI_STATE
        val started = wm.startScan()
        if (!started) {
            // Fallback to cached results
            val names = wm.scanResults.mapNotNull { it.SSID?.trim() }.filter { it.isNotEmpty() }.distinct()
            scannedSsids = names
            showSsidPicker = true
            scope.launch { snackbar.showSnackbar("Using cached Wi‑Fi results") }
        } else {
            scope.launch { snackbar.showSnackbar("Scanning for Wi‑Fi…") }
        }
    }

    // Listen for Wi‑Fi scan results and update the picker when available
    val scanReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val names = wm.scanResults.mapNotNull { it.SSID?.trim() }.filter { it.isNotEmpty() }.distinct()
                    scannedSsids = names
                    showSsidPicker = true
                }
            }
        }
    }
    DisposableEffect(Unit) {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(scanReceiver, filter)
        onDispose { runCatching { context.unregisterReceiver(scanReceiver) } }
    }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Connectivity Orchestrator – Demo") }) },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                PermsAndLocationBanner(
                    permsGranted = hasAll(wifiPerms),
                    locationOn = isLocationEnabled(),
                    onGrantPerms = { permLauncher.launch(wifiPerms) },
                    onOpenLocation = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
                )

                OutlinedTextField(
                    value = controller,
                    onValueChange = { controller = it; vm.controllerBaseUrl = it },
                    label = { Text("Controller Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { ssid = it; vm.aglrSsid = it },
                        label = { Text("AGLR SSID") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it; vm.aglrPass = it.ifBlank { null } },
                        label = { Text("AGLR Pass (opt)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        if (!hasAll(wifiPerms)) permLauncher.launch(wifiPerms) else scanSsids()
                    }) { Text("Scan & Pick SSID") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ubntIp,
                        onValueChange = { ubntIp = it; vm.ubntIp = it },
                        label = { Text("UBNT IP") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = mktIp,
                        onValueChange = { mktIp = it; vm.mktIp = it },
                        label = { Text("Mikrotik IP") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        when {
                            !hasAll(wifiPerms) -> permLauncher.launch(wifiPerms)
                            !isLocationEnabled() -> scope.launch { snackbar.showSnackbar("Turn on Location to connect/scan Wi-Fi") }
                            else -> {
                                Log.d("MainActivity", "Button: attempting connectAglr()")
                                try {
                                    vm.connectAglr()
                                } catch (t: Throwable) {
                                    Log.e("MainActivity", "connectAglr() from button crashed", t)
                                    scope.launch { snackbar.showSnackbar("Connect failed: ${t.message ?: "unknown error"}") }
                                }
                            }
                        }
                    }) { Text("Connect AGLR") }

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

                if (showSsidPicker) {
                    AlertDialog(
                        onDismissRequest = { showSsidPicker = false },
                        confirmButton = {},
                        title = { Text("Select SSID") },
                        text = {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(scannedSsids) { name ->
                                    TextButton(onClick = {
                                        ssid = name
                                        vm.aglrSsid = name
                                        showSsidPicker = false
                                        // Feedback and guarded connect attempt
                                        scope.launch { snackbar.showSnackbar("Selected: $name") }
                                        when {
                                            !hasAll(wifiPerms) -> permLauncher.launch(wifiPerms)
                                            !isLocationEnabled() -> scope.launch { snackbar.showSnackbar("Turn on Location to connect/scan Wi‑Fi") }
                                            else -> try {
                                                vm.connectAglr()
                                            } catch (t: Throwable) {
                                                Log.e("MainActivity", "connectAglr() after SSID pick crashed", t)
                                                scope.launch { snackbar.showSnackbar("Connect failed: ${t.message ?: "unknown error"}") }
                                            }
                                        }
                                    }) { Text(name) }
                                }
                                if (scannedSsids.isEmpty()) {
                                    item { Text("No SSIDs found. Try again after granting permission and being near the AP.") }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermsAndLocationBanner(
    permsGranted: Boolean,
    locationOn: Boolean,
    onGrantPerms: () -> Unit,
    onOpenLocation: () -> Unit,
) {
    if (permsGranted && locationOn) return
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val msg = when {
                !permsGranted && !locationOn -> "Needs Wi-Fi permission and Location ON to connect to AGLR"
                !permsGranted -> "Wi-Fi permission is required to connect to AGLR"
                else -> "Turn on Location to allow Wi-Fi scan/connect"
            }
            Text(msg, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!permsGranted) Button(onClick = onGrantPerms) { Text("Grant Permission") }
                if (!locationOn) OutlinedButton(onClick = onOpenLocation) { Text("Open Location Settings") }
            }
        }
    }
}
