# iPhone → Pixel Watch 2 Notification Bridge (ANCS over BLE)

## Project Goal

Mirror iPhone notifications to a Pixel Watch 2 using Bluetooth Low
Energy (BLE) and Apple's Notification Center Service (ANCS).

No cloud relay. All data remains local between the phone and watch.

------------------------------------------------------------------------

## Hardware / Software Targets

-   Watch: Google Pixel Watch 2
-   Watch OS: Wear OS 4+
-   Phone: iPhone (iOS 16+ recommended)

Technologies:

-   Bluetooth Low Energy (BLE)
-   Apple Notification Center Service (ANCS)
-   Swift (iOS)
-   Kotlin (Wear OS)

------------------------------------------------------------------------

# Architecture

    ┌─────────────────────────┐
    │        iPhone           │
    │                         │
    │  ANCS Server            │
    │  CoreBluetooth          │
    │  Notification Listener  │
    └─────────────┬───────────┘
                  │ BLE
                  │
    ┌─────────────▼───────────┐
    │     Pixel Watch 2       │
    │                         │
    │  ANCS Client            │
    │  Notification Renderer  │
    │  Local Notification DB  │
    └─────────────────────────┘

The watch acts as the **ANCS client** and retrieves notification details
directly from the phone.

------------------------------------------------------------------------

# Repository Layout

    notification-bridge/

      ios-app/
        BLEManager.swift
        NotificationService.swift
        AppDelegate.swift

      wearos-app/
        ble/
          AncsClient.kt
          BleManager.kt

        notifications/
          NotificationParser.kt
          NotificationRepository.kt

        ui/
          NotificationScreen.kt
          NotificationList.kt

------------------------------------------------------------------------

# iOS Implementation

Language: **Swift**

Frameworks:

-   CoreBluetooth
-   UserNotifications
-   BackgroundTasks

Capabilities required:

-   bluetooth-central
-   bluetooth-peripheral
-   background processing

## Notification Permission

    UNUserNotificationCenter.current().requestAuthorization(
     options: [.alert, .badge, .sound]
    )

## BLE Manager Structure

    class BLEManager: NSObject, CBCentralManagerDelegate {

        var centralManager: CBCentralManager

        func startBLE() {
            centralManager = CBCentralManager(delegate: self)
        }

        func centralManagerDidUpdateState() {
            if state == .poweredOn {
                startAdvertising()
            }
        }

    }

------------------------------------------------------------------------

# ANCS Protocol

Service UUID

    7905F431-B5CE-4E99-A40F-4B1E122D00D0

Characteristics

Notification Source

    9FBF120D-6301-42D9-8C58-25E699A21DBD

Control Point

    69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9

Data Source

    22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB

------------------------------------------------------------------------

# Wear OS Implementation

Language: **Kotlin**

Libraries:

-   android.bluetooth
-   androidx.wear
-   Jetpack Compose

Minimum OS:

Wear OS 4

------------------------------------------------------------------------

# Watch BLE Client

The watch scans for the iPhone advertising ANCS.

    bluetoothLeScanner.startScan(scanCallback)

    override fun onScanResult(result: ScanResult) {
       if(result.device.name.contains("iPhone")) {
           connect(result.device)
       }
    }

------------------------------------------------------------------------

# BLE Connection Flow

1.  Watch scans
2.  Watch connects
3.  Discover services
4.  Subscribe to Notification Source
5.  Receive event
6.  Request attributes
7.  Display notification

------------------------------------------------------------------------

# Notification Retrieval Sequence

When a notification occurs:

    iPhone → NotificationSource event

    payload:
    EventID
    Flags
    CategoryID
    NotificationUID

Watch sends:

    ControlPoint request:
    GetNotificationAttributes

Requested attributes:

-   Title
-   Subtitle
-   Message
-   AppIdentifier
-   Date

Response returns via:

    DataSource characteristic

------------------------------------------------------------------------

# Notification Model

    data class AncsNotification(
        val uid: Int,
        val appId: String,
        val title: String,
        val subtitle: String?,
        val message: String,
        val timestamp: Long
    )

------------------------------------------------------------------------

# Watch UI

## Notification Feed

-   Scrollable list
-   Newest first

Compose example:

    LazyColumn {
       items(notifications) {
          NotificationCard(it)
       }
    }

## Notification Detail

Display:

-   App icon
-   Title
-   Message
-   Timestamp

------------------------------------------------------------------------

# Storage

Recommended: **Room database**

Table schema:

    notifications
    -------------
    uid
    app
    title
    message
    timestamp

Retention limit:

200 notifications

------------------------------------------------------------------------

# BLE Reconnection Strategy

Reconnect if:

-   connection lost
-   screen turns on
-   watch wakes
-   bluetooth toggled

Pseudo:

    onConnectionLost()
      wait 3 seconds
      retry connect

------------------------------------------------------------------------

# Battery Optimisation

BLE scanning:

    scan interval: 10s
    scan window: 3s

Connection priority:

    balanced

------------------------------------------------------------------------

# Security Model

-   BLE pairing with LE Secure Connections
-   Encryption: AES‑CCM
-   No cloud infrastructure
-   No remote storage

All traffic:

    iPhone ↔ Watch
    encrypted BLE

------------------------------------------------------------------------

# Testing Plan

Test scenarios:

-   iPhone locked
-   iPhone unlocked
-   Bluetooth toggled
-   Airplane mode
-   Watch reboot
-   App killed

Notification sources:

-   iMessage
-   WhatsApp
-   Gmail
-   Calendar
-   Slack

Metrics:

-   delivery latency
-   connection stability
-   battery drain

------------------------------------------------------------------------

# Milestones

Week 1\
BLE connection

Week 2\
ANCS parsing

Week 3\
Notification UI

Week 4\
Reconnect + stability

------------------------------------------------------------------------

# Future Enhancements

Quick replies

-   Voice reply
-   preset messages

Notification controls

-   app allowlist
-   category mute

Additional features

-   phone battery status
-   watch ping
