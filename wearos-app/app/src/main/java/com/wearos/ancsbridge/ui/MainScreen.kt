package com.wearos.ancsbridge.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.wear.compose.material3.Icon
import com.wearos.ancsbridge.model.ConnectionState
import com.wearos.ancsbridge.viewmodel.MainViewModel

@SuppressLint("MissingPermission")
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var showPairScreen by remember { mutableStateOf(false) }

    if (showPairScreen) {
        PairNewDeviceScreen(
            viewModel = viewModel,
            onDismiss = { showPairScreen = false }
        )
    } else {
        HomeScreen(
            viewModel = viewModel,
            onPairNewDevice = { showPairScreen = true }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(viewModel: MainViewModel, onPairNewDevice: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader {
                Text("WearBridge")
            }
        }

        when (connectionState) {
            is ConnectionState.Connected -> {
                val deviceName = (connectionState as ConnectionState.Connected).deviceName ?: "iPhone"

                // Status
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Connected", fontSize = 14.sp)
                            Text(
                                "Notifications active",
                                fontSize = 11.sp,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }

                // iPhone info
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PhoneIphone,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(deviceName, fontSize = 14.sp)
                            Text(
                                "via ANCS over BLE",
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                // Clear notifications
                item {
                    Button(
                        onClick = { viewModel.clearAllNotifications() },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text("Clear Notifications", fontSize = 12.sp)
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                // Disconnect
                item {
                    Button(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        )
                    ) {
                        Text("Disconnect", fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            is ConnectionState.Connecting, is ConnectionState.Bonding -> {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            when (connectionState) {
                                is ConnectionState.Connecting ->
                                    "Connecting to ${(connectionState as ConnectionState.Connecting).deviceName ?: "iPhone"}…"
                                is ConnectionState.Bonding ->
                                    "Pairing with ${(connectionState as ConnectionState.Bonding).deviceName ?: "iPhone"}…"
                                else -> "Connecting…"
                            },
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            else -> {
                // Idle / Disconnected / Error — show reconnect or pair

                if (viewModel.hasBondedIPhone()) {
                    // Has a bonded device — show reconnecting state
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PhoneIphone,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (connectionState is ConnectionState.Error)
                                    (connectionState as ConnectionState.Error).message
                                else "iPhone not in range",
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.startService() },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Reconnect", fontSize = 12.sp)
                        }
                    }

                    item { Spacer(modifier = Modifier.height(4.dp)) }
                } else {
                    // No bonded device — prompt to pair
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PhoneIphone,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No iPhone paired",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Pair your iPhone to start receiving notifications",
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Always show Pair New Device button
                item {
                    Button(
                        onClick = onPairNewDevice,
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = if (viewModel.hasBondedIPhone())
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF374151))
                        else ButtonDefaults.buttonColors()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pair New Device", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun PairNewDeviceScreen(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()

    // Auto-dismiss when connected
    if (connectionState is ConnectionState.Connected) {
        onDismiss()
        return
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader {
                Text("Pair New Device")
            }
        }

        // Instructions
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Make sure the WearBridge companion app is open on your iPhone",
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        // Scanning state
        when (connectionState) {
            is ConnectionState.Connecting -> {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Connecting to ${(connectionState as ConnectionState.Connecting).deviceName ?: "iPhone"}…",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            is ConnectionState.Bonding -> {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Accept the pairing dialog on your iPhone",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            else -> {
                // Scan button
                item {
                    Button(
                        onClick = {
                            if (isScanning) viewModel.stopScan()
                            else viewModel.scanAndConnect()
                        },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text(
                            if (isScanning) "Stop Scanning" else "Start Scanning",
                            fontSize = 12.sp
                        )
                    }
                }

                // Scan status
                if (isScanning || scanStatus.isNotEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            if (scanStatus.isNotEmpty()) {
                                Text(
                                    scanStatus,
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Back button
        item {
            Button(
                onClick = {
                    viewModel.stopScan()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF374151)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back", fontSize = 12.sp)
                }
            }
        }
    }
}
