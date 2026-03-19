package com.wearos.ancsbridge.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.wearos.ancsbridge.R
import com.wearos.ancsbridge.ancs.AncsService
import com.wearos.ancsbridge.ancs.NotificationActionReceiver
import com.wearos.ancsbridge.ble.AncsConstants
import com.wearos.ancsbridge.ui.theme.AncsBridgeTheme

/**
 * Full-screen incoming call activity shown when ANCS reports an incoming call.
 * Shows caller name with Answer/Decline/Quick Reply buttons using Lucide icons.
 * Auto-dismisses when ANCS sends a REMOVE event for the call notification.
 */
class IncomingCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_NOTIFICATION_UID = "notification_uid"
        const val EXTRA_APP_NAME = "app_name"
        const val ACTION_CALL_ENDED = "com.wearos.ancsbridge.CALL_ENDED"
        const val ACTION_CALLER_NAME_UPDATED = "com.wearos.ancsbridge.CALLER_NAME_UPDATED"

        val QUICK_REPLIES = listOf(
            "Can't talk right now.",
            "Hey! Can't talk rn, what's up?",
            "I'll call you back shortly.",
            "In a meeting, will call later.",
            "Driving right now, will call later."
        )
    }

    private var notificationUid: Long = -1
    private var showingReplies = mutableStateOf(false)
    private var callerNameState = mutableStateOf("Unknown Caller")

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CALL_ENDED -> finish()
                ACTION_CALLER_NAME_UPDATED -> {
                    val name = intent.getStringExtra(EXTRA_CALLER_NAME)
                    if (!name.isNullOrEmpty()) {
                        callerNameState.value = name
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // Dismiss the keyguard so the call screen is interactive
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

        // Keep screen on while call screen is showing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        callerNameState.value = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown Caller"
        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Phone"
        notificationUid = intent.getLongExtra(EXTRA_NOTIFICATION_UID, -1)

        val filter = IntentFilter().apply {
            addAction(ACTION_CALL_ENDED)
            addAction(ACTION_CALLER_NAME_UPDATED)
        }
        registerReceiver(callEndedReceiver, filter, RECEIVER_NOT_EXPORTED)

        setContent {
            AncsBridgeTheme {
                val showReplies by showingReplies
                val callerName by callerNameState

                if (showReplies) {
                    QuickReplyScreen(
                        callerName = callerName,
                        replies = QUICK_REPLIES,
                        onReplySelected = { reply ->
                            declineWithMessage(reply)
                        },
                        onBack = {
                            showingReplies.value = false
                        }
                    )
                } else {
                    IncomingCallScreen(
                        callerName = callerName,
                        appName = appName,
                        onAnswer = {
                            performAction(AncsConstants.ACTION_POSITIVE)
                            finish()
                        },
                        onDecline = {
                            performAction(AncsConstants.ACTION_NEGATIVE)
                            finish()
                        },
                        onQuickReply = {
                            showingReplies.value = true
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (_: IllegalArgumentException) { }
    }

    private fun performAction(actionId: Int) {
        if (notificationUid == -1L) return
        val intent = Intent(this, AncsService::class.java).apply {
            action = AncsService.ACTION_PERFORM_NOTIFICATION_ACTION
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, notificationUid)
            putExtra(AncsService.EXTRA_ACTION_ID, actionId)
        }
        startService(intent)
    }

    private fun declineWithMessage(message: String) {
        performAction(AncsConstants.ACTION_NEGATIVE)

        val intent = Intent(this, AncsService::class.java).apply {
            action = AncsService.ACTION_SEND_QUICK_REPLY
            putExtra(AncsService.EXTRA_QUICK_REPLY_TEXT, message)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_UID, notificationUid)
            putExtra("caller_name", callerNameState.value)
        }
        startService(intent)

        finish()
    }
}

@Composable
fun IncomingCallScreen(
    callerName: String,
    appName: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onQuickReply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = appName,
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = callerName,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Incoming Call",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Answer / Quick Reply / Decline row
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline button (red) — Lucide phone-off
            Button(
                onClick = onDecline,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444)
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_call_decline),
                    contentDescription = "Decline",
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Quick reply button (blue) — Lucide message-circle
            Button(
                onClick = onQuickReply,
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6)
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chat_reply),
                    contentDescription = "Quick Reply",
                    modifier = Modifier.size(20.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Answer button (green) — Lucide phone
            Button(
                onClick = onAnswer,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF22C55E)
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_call_answer),
                    contentDescription = "Answer",
                    modifier = Modifier.size(22.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun QuickReplyScreen(
    callerName: String,
    replies: List<String>,
    onReplySelected: (String) -> Unit,
    onBack: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text(
                text = "Decline & Reply",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White
            )
        }

        item {
            Text(
                text = callerName,
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        items(replies) { reply ->
            FilledTonalButton(
                onClick = { onReplySelected(reply) },
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Text(
                    text = reply,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(0.7f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF374151)
                )
            ) {
                Text("Back", fontSize = 13.sp)
            }
        }
    }
}
