package com.example.heyaaron

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.heyaaron.ui.theme.HeyAaronTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeyAaronTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        onStartServiceClick = { startAaronService() },
                        onTestVoiceClick = { testVoice() }
                    )
                }
            }
        }
    }

    private fun startAaronService() {
        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } else {
            val intent = Intent(this, AaronService::class.java)
            startService(intent)
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
fun MainScreen(onStartServiceClick: () -> Unit, onTestVoiceClick: () -> Unit) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("AaronPrefs", Context.MODE_PRIVATE) }
    var isAaronVoiceEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("isAaronVoiceEnabled", true)) }
    var showDialog by remember { mutableStateOf(false) }
    var elementsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(1000)
        elementsVisible = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.aaron_background),
            contentDescription = "Background Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        AnimatedVisibility(
            visible = elementsVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 1000)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.aaron_face),
                        contentDescription = "Aaron Face",
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(8.dp, shape = MaterialTheme.shapes.small)
                            .clickable { showDialog = true }
                            .padding(end = 8.dp)
                    )
                    Switch(
                        checked = isAaronVoiceEnabled,
                        onCheckedChange = {
                            isAaronVoiceEnabled = it
                            with(sharedPreferences.edit()) {
                                putBoolean("isAaronVoiceEnabled", it)
                                apply()
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF1976D2))
                    )
                }

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("Enable Aaron's Voice") },
                        text = { Text("Toggle this on to listen to Aaron's voice.") },
                        confirmButton = {
                            TextButton(onClick = { showDialog = false }) {
                                Text("OK")
                            }
                        }
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Hey Aaron",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color.White,
                        modifier = Modifier.shadow(24.dp)
                    )

                    Spacer(modifier = Modifier.height(64.dp))

                    Button(
                        onClick = onStartServiceClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1976D2),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 16.dp),
                        modifier = Modifier.shadow(12.dp, shape = MaterialTheme.shapes.medium)
                    ) {
                        Text("Wake up Aaron")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onTestVoiceClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1976D2),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 16.dp),
                        modifier = Modifier.shadow(12.dp, shape = MaterialTheme.shapes.medium)
                    ) {
                        Text("Ssup, Aaron?")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HeyAaronTheme {
        MainScreen({}, {})
    }
}
