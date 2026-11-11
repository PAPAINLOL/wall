package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BorderStyle
import com.example.data.model.DisplaySettings
import com.example.data.model.FeedSort
import com.example.data.model.WallpaperTarget
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.RedditOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: DisplaySettings,
    onSetInterval: (Int) -> Unit,
    onSetBorderStyle: (BorderStyle) -> Unit,
    onSetFeedSort: (FeedSort) -> Unit,
    onSetWallpaperTarget: (WallpaperTarget) -> Unit,
    onTogglePreventDuplicates: (Boolean) -> Unit,
    onToggleAutoSetWallpaper: (Boolean) -> Unit,
    onPickGalleryImage: () -> Unit,
    onOpenSubreddits: () -> Unit,
    onDismiss: () -> Unit,
    cachedCount: Int = 0,
    cacheSize: String = "0 MB",
    onCacheAll: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onSetMaxPostsLimit: (Int) -> Unit = {},
    onSetRedditApiCredentials: (String, String) -> Unit = { _, _ -> },
    onOpenLogs: () -> Unit = {},
    onRefreshAllNew: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var clientIdInput by remember { mutableStateOf(settings.redditClientId) }
    var clientSecretInput by remember { mutableStateOf(settings.redditClientSecret) }
    var showApiSettings by remember { mutableStateOf(settings.redditClientId.isNotBlank()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "Configuración & Modo Proyector",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Personaliza la reproducción, ordenación 'Top'/'New', caché local y límite de fotos",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action: Actualizar con todas las nuevas (User request)
            Button(
                onClick = {
                    onDismiss()
                    onRefreshAllNew()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("refresh_all_new_sheet_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Actualizar con todas las nuevas", fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Subreddit quick launcher button
            Button(
                onClick = {
                    onDismiss()
                    onOpenSubreddits()
                },
                colors = ButtonDefaults.buttonColors(containerColor = RedditOrange),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manage_subreddits_sheet_button")
            ) {
                Icon(Icons.Default.Tv, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gestionar Subreddits (sssfdg & AlbumCovers)", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Open system logs (User request)
            Button(
                onClick = {
                    onDismiss()
                    onOpenLogs()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_logs_sheet_button")
            ) {
                Icon(Icons.Default.Terminal, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Logs Detallados del Sistema & Red", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Add from local gallery button
            OutlinedButton(
                onClick = onPickGalleryImage,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_gallery_photo_button")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Añadir Foto desde tu Galería", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Límite Máximo de Fotos (Auto-Paginación al acabar fotos)
            SectionHeader(
                icon = Icons.Default.ViewCarousel,
                title = "Límite Máximo de Fotos (Paginación)",
                subtitle = "Al agotarse las fotos se piden más automáticamente hasta este tope"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DisplaySettings.AVAILABLE_MAX_LIMITS.forEach { (limitVal, label) ->
                    val isSelected = settings.maxPostsLimit == limitVal
                    Surface(
                        color = if (isSelected) NeonCyan else DarkSurfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSetMaxPostsLimit(limitVal) }
                            .testTag("limit_$limitVal")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "$limitVal",
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            Text(
                text = "Límite actual: ${settings.maxPostsLimit} fotos en carrusel",
                color = NeonCyan,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Modo de Ordenación (Top vs New)
            SectionHeader(
                icon = Icons.Default.TrendingUp,
                title = "Modo de Selección (Top vs New)",
                subtitle = "Alterna entre fotos más votadas (Top) o más recientes (New)"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(FeedSort.TOP, FeedSort.NEW).forEach { sort ->
                    val isSelected = settings.feedSort == sort
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSetFeedSort(sort) }
                            .testTag("sort_mode_${sort.name}")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = sort.icon, fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = sort.displayName,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) NeonCyan else Color.White,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = sort.description,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "✓ Activo",
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Caché Local de Imágenes
            SectionHeader(
                icon = Icons.Default.Storage,
                title = "Caché Local de Imágenes",
                subtitle = "Almacena fotos en el dispositivo para que funcione sin conexión"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Fotos guardadas en disco",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "$cachedCount fotos guardadas • $cacheSize ocupados",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCacheAll,
                            colors = ButtonDefaults.buttonColors(containerColor = RedditOrange),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Guardar todo", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onClearCache,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFF5252))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Limpiar", fontSize = 12.sp, color = Color(0xFFFF5252))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: API Key de Reddit (Opcional)
            SectionHeader(
                icon = Icons.Default.Key,
                title = "API Key / Credenciales de Reddit (Opcional)",
                subtitle = "Usa endpoints libres por defecto, o añade tus credenciales para evitar límites"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "La aplicación funciona sin API key mediante enlaces públicos y respaldos, pero si tienes una API Key de Reddit para desarrolladores, puedes ponerla aquí:",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = clientIdInput,
                        onValueChange = { clientIdInput = it },
                        label = { Text("Reddit Client ID", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedditOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = clientSecretInput,
                        onValueChange = { clientSecretInput = it },
                        label = { Text("Reddit Client Secret", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedditOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            onSetRedditApiCredentials(clientIdInput.trim(), clientSecretInput.trim())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RedditOrange),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Guardar Credenciales", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 5: Estilo de Bordes (NO CROP)
            SectionHeader(
                icon = Icons.Default.Palette,
                title = "Ajuste de Imagen y Bordes",
                subtitle = "¡La foto nunca se recorta! Relleno inteligente de bordes faltantes"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BorderStyle.values().forEach { style ->
                    val isSelected = settings.borderStyle == style
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) RedditOrange.copy(alpha = 0.2f) else DarkSurfaceContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetBorderStyle(style) }
                            .testTag("border_style_${style.name}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = style.title,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) RedditOrange else Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = style.description,
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seleccionado",
                                    tint = RedditOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 6: Intervalo de Diapositiva
            SectionHeader(
                icon = Icons.Default.Timer,
                title = "Intervalo de Diapositivas (TV & Proyector)",
                subtitle = "Segundos entre cada cambio automático de foto"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(5 to "5s", 10 to "10s", 15 to "15s", 30 to "30s", 60 to "1m", 300 to "5m").forEach { (sec, label) ->
                    val isSelected = settings.intervalSeconds == sec
                    Surface(
                        color = if (isSelected) RedditOrange else DarkSurfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSetInterval(sec) }
                            .testTag("interval_$sec")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = label,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 7: Fondo de Pantalla & Reglas
            SectionHeader(
                icon = Icons.Default.Wallpaper,
                title = "Reglas de Fondo y Duplicados",
                subtitle = "Control de repetición y sincronización de fondo de Android"
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // No repetir
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo 'No Repetir' Fotos",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Lleva un registro de historial para no repetir fotos ya mostradas",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.preventDuplicates,
                            onCheckedChange = onTogglePreventDuplicates,
                            colors = SwitchDefaults.colors(checkedThumbColor = RedditOrange)
                        )
                    }

                    Divider(
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    // Auto set wallpaper on slide
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cambiar fondo del móvil con cada pase",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Aplica automáticamente al sistema cada foto nueva sin recortar",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = settings.autoSetWallpaperOnSlide,
                            onCheckedChange = onToggleAutoSetWallpaper,
                            colors = SwitchDefaults.colors(checkedThumbColor = RedditOrange)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = RedditOrange.copy(alpha = 0.2f),
            shape = CircleShape,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = RedditOrange, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}
