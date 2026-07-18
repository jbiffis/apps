package dev.jbiffis.caddie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.garmin.GarminClient
import dev.jbiffis.caddie.data.garmin.GarminGolfImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Garmin Connect cloud sync: the watch keeps its normal pairing with the
 * Garmin Connect app; we pull rounds from Garmin's cloud instead of BLE.
 */
@Composable
fun GarminConnectSection(app: CaddieApp, log: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(app.garminAuth.isLoggedIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Garmin Connect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Recommended: leave the watch paired with the Garmin Connect app as usual. " +
                    "Caddie signs in to Garmin's cloud and pulls your scorecards, shots and GPS tracks from there.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            if (!loggedIn) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Garmin account email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            busy = true; status = "Signing in…"
                            scope.launch {
                                status = try {
                                    withContext(Dispatchers.IO) { app.garminAuth.login(email.trim(), password) }
                                    loggedIn = true
                                    password = ""
                                    "Signed in — tap Sync now"
                                } catch (e: Exception) {
                                    e.message ?: "Sign-in failed"
                                }
                                busy = false
                            }
                        },
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    ) { Text("Sign in") }
                    if (busy) CircularProgressIndicator(Modifier.padding(6.dp))
                }
                Text(
                    "Credentials go to garmin.com only. Two-factor accounts aren't supported yet.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    "Signed in as ${app.garminAuth.username ?: "?"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            busy = true; status = "Syncing…"
                            scope.launch {
                                status = try {
                                    withContext(Dispatchers.IO) {
                                        val client = GarminClient(app.garminAuth, log)
                                        GarminGolfImport(app.db.dao(), app.repository, log).sync(client)
                                    }
                                } catch (e: Exception) {
                                    log("Sync failed: $e")
                                    "Sync failed: ${e.message?.take(120)}"
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) { Text("Sync now") }
                    TextButton(onClick = {
                        app.garminAuth.logout(); loggedIn = false; status = null
                    }, enabled = !busy) { Text("Sign out") }
                    if (busy) CircularProgressIndicator(Modifier.padding(6.dp))
                }
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
