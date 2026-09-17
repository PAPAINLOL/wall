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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.model.SubredditInfo
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.RedditOrange
import com.example.util.HumorHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubredditsSheet(
    subreddits: List<SubredditInfo>,
    onToggleSubreddit: (name: String, enabled: Boolean) -> Unit,
    onAddSubreddit: (name: String, displayName: String, emoji: String) -> Unit,
    onAddMultipleSubreddits: (input: String) -> Unit,
    onDeleteSubreddit: (name: String) -> Unit,
    onRestoreDefaultSubreddits: () -> Unit,
    onRefreshPosts: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddDialog by remember { mutableStateOf(false) }
    val humorousTip = remember { HumorHelper.getHumorousSubredditTip() }

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
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Subreddits & Álbumes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Añade, elimina u oculta temporalmente fuentes para el fondo",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRestoreDefaultSubreddits,
                        modifier = Modifier.testTag("restore_defaults_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Restaurar subreddits iniciales",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(
                        onClick = onRefreshPosts,
                        modifier = Modifier.testTag("refresh_subreddits_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recargar fotos de fuentes activas",
                            tint = NeonCyan
                        )
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RedditOrange),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("add_subreddit_open_dialog")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Añadir", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Humorous tip banner
            Surface(
                color = NeonPurple.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = humorousTip,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subreddit list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(subreddits, key = { it.name }) { sub ->
                    SubredditRowItem(
                        subreddit = sub,
                        onToggle = { isEnabled -> onToggleSubreddit(sub.name, isEnabled) },
                        onDelete = { onDeleteSubreddit(sub.name) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubredditDialog(
            onConfirmSingle = { name, displayName, emoji ->
                onAddSubreddit(name, displayName, emoji)
                showAddDialog = false
            },
            onConfirmMultiple = { input ->
                onAddMultipleSubreddits(input)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun SubredditRowItem(
    subreddit: SubredditInfo,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (subreddit.isEnabled) DarkSurfaceContainer else Color(0xFF14151E)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon / Emoji
            Surface(
                color = if (subreddit.isEnabled) RedditOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = subreddit.iconEmoji,
                        fontSize = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = subreddit.displayName,
                        fontWeight = FontWeight.Bold,
                        color = if (subreddit.isEnabled) Color.White else Color.White.copy(alpha = 0.45f),
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "r/${subreddit.name}",
                        color = RedditOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subreddit.isEnabled) {
                        Text(
                            text = "👁️ Activo en rotación",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "🙈 Oculto temporalmente",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Delete Button (Available for all subreddits to satisfy user removal requirement)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_sub_${subreddit.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar subreddit de la lista",
                    tint = Color(0xFFFF5252).copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Temporarily Hide / Unhide Toggle
            Switch(
                checked = subreddit.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = RedditOrange,
                    checkedTrackColor = RedditOrange.copy(alpha = 0.4f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                ),
                modifier = Modifier.testTag("toggle_sub_${subreddit.name}")
            )
        }
    }
}

@Composable
fun AddSubredditDialog(
    onConfirmSingle: (name: String, displayName: String, emoji: String) -> Unit,
    onConfirmMultiple: (input: String) -> Unit,
    onDismiss: () -> Unit
) {
    var isMultipleMode by remember { mutableStateOf(false) }
    var subName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🔥") }
    var multipleInput by remember { mutableStateOf("") }

    val popularSuggestions = listOf(
        "albumartPorn" to "🎵",
        "blurrypicturesofcats" to "🐱",
        "hmmm" to "🤔",
        "wallpapers" to "🖼️",
        "fakealbumcovers" to "💿",
        "vinyl" to "📻",
        "indieheads" to "🎸",
        "Cyberpunk" to "🌆"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = if (isMultipleMode) "Añadir Múltiples Subreddits" else "Añadir Nuevo Subreddit",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                // Mode Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (!isMultipleMode) RedditOrange else DarkSurfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isMultipleMode = false }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = "Individual",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Surface(
                        color = if (isMultipleMode) RedditOrange else DarkSurfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isMultipleMode = true }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = "Múltiples (+)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (isMultipleMode) {
                    Text(
                        text = "Introduce varios subreddits separados por comas o saltos de línea:",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = multipleInput,
                        onValueChange = { multipleInput = it },
                        placeholder = { Text("albumartPorn, vinyl, hmmm, cassetteculture") },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedditOrange,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_multiple_subs")
                    )
                } else {
                    Text(
                        text = "Nombre del subreddit para añadir sin API key:",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = subName,
                        onValueChange = { subName = it },
                        label = { Text("Nombre (ej. albumartPorn, vinyl)") },
                        prefix = { Text("r/", color = RedditOrange) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedditOrange,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_sub_name")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Título descriptivo (opcional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RedditOrange,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Sugerencias populares:",
                    color = NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    popularSuggestions.take(3).forEach { (sName, sEmoji) ->
                        Surface(
                            color = DarkSurfaceContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                if (isMultipleMode) {
                                    multipleInput = if (multipleInput.isBlank()) sName else "$multipleInput, $sName"
                                } else {
                                    subName = sName
                                    displayName = sName
                                    emoji = sEmoji
                                }
                            }
                        ) {
                            Text(
                                text = "$sEmoji r/$sName",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isMultipleMode) {
                        if (multipleInput.isNotBlank()) {
                            onConfirmMultiple(multipleInput)
                        }
                    } else {
                        if (subName.isNotBlank()) {
                            onConfirmSingle(subName, displayName, emoji)
                        }
                    }
                },
                enabled = if (isMultipleMode) multipleInput.isNotBlank() else subName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RedditOrange),
                modifier = Modifier.testTag("btn_confirm_add_sub")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}
