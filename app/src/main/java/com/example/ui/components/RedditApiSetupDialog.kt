package com.example.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RedditOrange
import com.example.util.RedditApiCredentialsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedditApiSetupDialog(
    initialClientId: String,
    initialClientSecret: String,
    onSaveAndRefresh: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var clientId by remember { mutableStateOf(initialClientId) }
    var clientSecret by remember { mutableStateOf(initialClientSecret) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp)
                .border(1.dp, RedditOrange.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = RedditOrange.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = RedditOrange)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "API Oficial de Reddit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Garantiza acceso 100% real sin bloqueos HTTP 403",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (initialClientId.isNotBlank()) {
                    Surface(
                        color = Color(0xFF2E7D32).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ Credenciales OAuth activas (Client ID configurado). Puedes probarlas o actualizarlas abajo.",
                            color = Color(0xFF81C784),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Step-by-step instructions card
                Surface(
                    color = DarkSurfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Cómo configurar tu app de Reddit ('script' o 'installed app'):",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Abre reddit.com/prefs/apps con tu cuenta\n" +
                                    "2. Crea o usa una app tipo 'personal use script' o 'installed app'\n" +
                                    "3. Pega el Client ID (código corto bajo el nombre de la app)\n" +
                                    "4. Si es 'script', pega también el Secret (déjalo vacío si es 'installed app')\n" +
                                    "5. Pulsa 'Probar' y luego 'Guardar y Cargar'",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/prefs/apps"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RedditOrange.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = RedditOrange, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Abrir reddit.com/prefs/apps en navegador", color = RedditOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input fields
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Reddit Client ID", fontSize = 12.sp) },
                    placeholder = { Text("Ej. X8a_9mK1b2c3d4", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedditOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clip.isNullOrBlank()) clientId = clip.trim()
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Pegar", tint = Color.White.copy(alpha = 0.6f))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_reddit_client_id_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text("Reddit Client Secret", fontSize = 12.sp) },
                    placeholder = { Text("Ej. aB1cD2eF3gH4iJ5kL6mN7oP8qR9", fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RedditOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                            if (!clip.isNullOrBlank()) clientSecret = clip.trim()
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Pegar", tint = Color.White.copy(alpha = 0.6f))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("dialog_reddit_client_secret_input")
                )

                // Live test result indicator
                if (testResult != null) {
                    val (success, message) = testResult!!
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = if (success) Color(0xFF2E7D32).copy(alpha = 0.25f) else Color(0xFFC62828).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (success) "✓ $message" else "✗ $message",
                            color = if (success) Color(0xFF81C784) else Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Test button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTesting = true
                                testResult = null
                                val result = RedditApiCredentialsManager.testCredentials(clientId, clientSecret)
                                isTesting = false
                                if (result.isSuccess) {
                                    testResult = Pair(true, "¡Credenciales válidas! Token recibido.")
                                } else {
                                    testResult = Pair(false, result.exceptionOrNull()?.message ?: "Error al verificar")
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("dialog_test_credentials_button")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Science, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Probar", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Save and refresh button
                    Button(
                        onClick = {
                            onSaveAndRefresh(clientId.trim(), clientSecret.trim())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedditOrange),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f).testTag("dialog_save_credentials_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar y Cargar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Cerrar", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}
