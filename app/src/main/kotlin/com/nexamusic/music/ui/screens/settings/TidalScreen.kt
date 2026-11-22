package com.nexamusic.music.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nexamusic.music.App
import com.nexamusic.music.BuildConfig
import com.nexamusic.music.ui.ios.foundation.IosTheme
import kotlinx.coroutines.launch

private val tidalQualityOptions = listOf(
    "HI_RES" to "Hi-Res",
    "HI_RES_DOLBY" to "Hi-Res Dolby",
    "CD" to "CD Lossless",
    "HIGH" to "High",
    "LOW" to "Low",
)

@androidx.compose.runtime.Composable
fun TidalScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val scope = rememberCoroutineScope()
    var manager by remember { mutableStateOf<com.nexamusic.music.tidal.TidalSdkManager?>(null) }
    var connected by rememberSaveable { mutableStateOf(false) }
    var selectedQuality by rememberSaveable { mutableStateOf("CD") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        manager = app.awaitTidalSdkManager()
        connected = manager?.isUserLoggedIn == true
        loading = false
    }

    SettingsPage(
        navController = navController,
        title = "TIDAL",
        horizontalPadding = 20.dp,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = IosTheme.colors.secondarySystemGroupedBackground),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (connected) "TIDAL connected" else "Connect your TIDAL account",
                    style = IosTheme.typography.title3,
                    color = IosTheme.colors.label,
                )
                Text(
                    text = when {
                        loading -> "Checking TIDAL configuration…"
                        manager == null -> "Add your TIDAL developer Client ID and registered redirect URI to local.properties to enable sign-in."
                        connected -> "Your TIDAL account is authorised in NexaMusic."
                        else -> "Sign in through TIDAL's official Authorization Code login."
                    },
                    style = IosTheme.typography.body,
                    color = IosTheme.colors.secondaryLabel,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = !loading && manager != null && !connected,
                        onClick = {
                            scope.launch {
                                val current = manager ?: app.awaitTidalSdkManager()
                                if (current == null) {
                                    Toast.makeText(context, "TIDAL is not configured yet.", Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            current.createLoginUri(BuildConfig.TIDAL_REDIRECT_URI),
                                        ),
                                    )
                                }.onFailure {
                                    Toast.makeText(context, "Couldn't open TIDAL sign-in: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    ) {
                        Text(if (connected) "Connected" else "Connect TIDAL")
                    }
                    if (connected) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching { manager?.logout() }
                                    connected = false
                                }
                            },
                        ) {
                            Text("Log out")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "STREAMING QUALITY",
            style = IosTheme.typography.footnote,
            color = IosTheme.colors.secondaryLabel,
            modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = IosTheme.colors.secondarySystemGroupedBackground),
        ) {
            Column {
                tidalQualityOptions.forEach { (value, label) ->
                    androidx.compose.material3.TextButton(
                        onClick = {
                            selectedQuality = value
                            runCatching { manager?.setQuality(value) }
                                .onFailure {
                                    Toast.makeText(context, it.message ?: "Couldn't change TIDAL quality.", Toast.LENGTH_SHORT).show()
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (selectedQuality == value) "✓  $label" else label,
                            color = IosTheme.colors.label,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Quality options mirror the NexaMusic 8SPINE module style. Actual track quality and full-length playback depend on TIDAL approval, account entitlement and track availability. Choosing CD Lossless does not guarantee that every track is delivered in CD quality.",
            style = IosTheme.typography.footnote,
            color = IosTheme.colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(30.dp))
    }
}
