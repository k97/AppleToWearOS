package com.wearos.ancsbridge.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.wearos.ancsbridge.model.ConnectionState
import com.wearos.ancsbridge.viewmodel.MainViewModel

// iPhone Bluetooth address from Settings > General > About > Bluetooth
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
                Text("ANCS Bridge")
            }
        }

        // Connection status
        item {
            ConnectionStatusCard(connectionState)
        }

        // Actions based on state
        when (connectionState) {
            is ConnectionState.Idle, is ConnectionState.Disconnected, is ConnectionState.Error -> {
                if (viewModel.hasBondedIPhone()) {
                    item {
                        Button(
                            onClick = { viewModel.startService() },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            Text("Connect to iPhone")
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (isScanning) viewModel.stopScan()
                            else viewModel.scanAndConnect()
                        },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text(if (isScanning) "Stop" else "Scan & Pair")
                    }
                }

                if (scanStatus.isNotEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                scanStatus,
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

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
                                is ConnectionState.Connecting -> "Connecting..."
                                is ConnectionState.Bonding -> "Pairing..."
                                else -> "Scanning..."
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            is ConnectionState.Connected -> {
                item {
                    Button(
                        onClick = { viewModel.disconnect() },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(state: ConnectionState) {
    val text = when (state) {
        is ConnectionState.Idle -> "Not connected"
        is ConnectionState.Scanning -> "Scanning..."
        is ConnectionState.Connecting -> "Connecting to ${state.deviceName ?: "iPhone"}"
        is ConnectionState.Bonding -> "Pairing with ${state.deviceName ?: "iPhone"}"
        is ConnectionState.Connected -> "Connected to ${state.deviceName ?: "iPhone"}"
        is ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Error -> "Error: ${state.message}"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center
        )
    }
}
