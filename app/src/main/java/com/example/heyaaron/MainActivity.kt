package com.example.heyaaron

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.heyaaron.ui.theme.HeyAaronTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeyAaronTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // We pass the helper functions to the screen
                    MainScreen(
                        onStartService = { startAaronService() },
                        onTestVoice = { testVoice() },
                        checkNotificationAccess = { isNotificationServiceEnabled() }
                    )
                }
            }
        }
    }

    private fun startAaronService() {
        // Double check notification access before starting
        if (!isNotificationServiceEnabled()) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } else {
            val intent = Intent(this, AaronService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun testVoice() {
        val intent = Intent(this, AaronService::class.java)
        intent.action = "ACTION_TEST_VOICE"
        startService(intent)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }
}

@Composable
fun MainScreen(
    onStartService: () -> Unit,
    onTestVoice: () -> Unit,
    checkNotificationAccess: () -> Boolean
) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(false) }

    // Prepare the list of permissions we need
    val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Create the launcher that handles the result
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { perms ->
            // Update state: true only if ALL requested permissions are granted
            permissionsGranted = perms.values.all { it }
        }
    )

    // Check permissions as soon as the app starts
    LaunchedEffect(Unit) {
        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        permissionsGranted = allGranted
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!permissionsGranted) {
            // STATE 1: Permissions Missing
            Text(
                text = "Hey Aaron needs permissions to hear you.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            }) {
                Text("Grant Mic & Notification Permissions")
            }
        } else {
            // STATE 2: Permissions Granted
            Text(
                text = "Ready to listen!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onStartService) {
                Text("Start Listening")
            }

            // Helpful text about Notification Access
            if (!checkNotificationAccess()) {
                Text(
                    text = "(Requires Notification Access)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onTestVoice) {
                Text("Test Voice Now")
            }
        }
    }
}