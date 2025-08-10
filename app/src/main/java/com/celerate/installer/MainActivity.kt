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
