package com.wearos.ancsbridge.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.wear.compose.material3.Icon
import com.wearos.ancsbridge.model.ConnectionState
import com.wearos.ancsbridge.viewmodel.MainViewModel

@SuppressLint("MissingPermission")
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanStatus by viewModel.scanStatus.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ListHeader {
                Text("AppleToWearOS")
            }
        }

        // Status card
        item {
            StatusCard(connectionState)
        }

        // Connected state: show iPhone info + actions
        when (connectionState) {
            is ConnectionState.Connected -> {
                val deviceName = (connectionState as ConnectionState.Connected).deviceName ?: "iPhone"

                // iPhone card
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
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                deviceName,
                                fontSize = 14.sp
                            )
                            Text(
                                "ANCS Active",
                                fontSize = 11.sp,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }

                item {
                    Button(
                        onClick = { viewModel.clearAllNotifications() },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text("Clear Notifications", fontSize = 12.sp)
                    }
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    Button(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF374151)
                        )
                    ) {
                        Text("Disconnect", fontSize = 12.sp)
                    }
                }
            }

            // Connecting / Scanning / Bonding states
            is ConnectionState.Scanning, is ConnectionState.Connecting, is ConnectionState.Bonding -> {
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
                                else -> "Scanning…"
                            },
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Idle / Disconnected / Error states
            is ConnectionState.Idle, is ConnectionState.Disconnected, is ConnectionState.Error -> {
                if (viewModel.hasBondedIPhone()) {
                    item {
                        Button(
                            onClick = { viewModel.startService() },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Connect to iPhone", fontSize = 12.sp)
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (isScanning) viewModel.stopScan()
                            else viewModel.scanAndConnect()
                        },
                        modifier = Modifier.fillMaxWidth(0.9f),
                        colors = if (viewModel.hasBondedIPhone())
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF374151))
                        else
                            ButtonDefaults.buttonColors()
                    ) {
                        Text(
                            if (isScanning) "Stop" else "Scan & Pair",
                            fontSize = 12.sp
                        )
                    }
                }

                if (scanStatus.isNotEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                            }
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
}

@Composable
fun StatusCard(state: ConnectionState) {
    val isConnected = state is ConnectionState.Connected

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (isConnected) Color(0xFF34D399) else Color(0xFFFBBF24),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = if (isConnected) "Notifications Active" else statusTitle(state),
                fontSize = 14.sp
            )
            Text(
                text = if (isConnected) "Forwarding from iPhone" else statusSubtitle(state),
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF)
            )
        }
    }
}

private fun statusTitle(state: ConnectionState): String = when (state) {
    is ConnectionState.Idle -> "Not Connected"
    is ConnectionState.Disconnected -> "Disconnected"
    is ConnectionState.Error -> "Connection Error"
    is ConnectionState.Scanning -> "Scanning"
    is ConnectionState.Connecting -> "Connecting"
    is ConnectionState.Bonding -> "Pairing"
    is ConnectionState.Connected -> "Connected"
}

private fun statusSubtitle(state: ConnectionState): String = when (state) {
    is ConnectionState.Idle -> "Tap Connect or Scan to start"
    is ConnectionState.Disconnected -> "iPhone connection lost"
    is ConnectionState.Error -> state.message
    is ConnectionState.Scanning -> "Looking for iPhone…"
    is ConnectionState.Connecting -> "Establishing connection…"
    is ConnectionState.Bonding -> "Complete pairing on iPhone"
    is ConnectionState.Connected -> "ANCS active"
}
