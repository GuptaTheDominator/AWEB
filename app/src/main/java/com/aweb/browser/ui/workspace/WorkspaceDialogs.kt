package com.aweb.browser.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aweb.browser.data.entity.WorkspaceEntity
import com.aweb.browser.data.repository.WorkspaceRepository

// ── Create workspace dialog ────────────────────────────────────────────────

@Composable
fun CreateWorkspaceDialog(
    onConfirm : (name: String, colorHex: String) -> Unit,
    onDismiss : () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(WorkspaceRepository.DEFAULT_COLORS.first()) }

    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor  = Color(0xFFCCCCCC),
        title = { Text("New Workspace", fontWeight = FontWeight.Bold) },
        text  = {
            Column {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Workspace name") },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFF9C6FFF),
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedLabelColor    = Color(0xFF9C6FFF),
                        unfocusedLabelColor  = Color(0xFF888888),
                        focusedTextColor     = Color.White,
                        unfocusedTextColor   = Color.White,
                        cursorColor          = Color(0xFF9C6FFF),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text("Colour", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns        = GridCells.Fixed(7),
                    modifier       = Modifier.height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(WorkspaceRepository.DEFAULT_COLORS) { hex ->
                        val c = runCatching {
                            Color(android.graphics.Color.parseColor(hex))
                        }.getOrDefault(Color.Gray)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(
                                    if (hex == selectedColor)
                                        Modifier.border(2.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, selectedColor) },
            ) {
                Text("Create", color = Color(0xFF9C6FFF), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF888888))
            }
        },
    )
}

// ── Rename dialog ─────────────────────────────────────────────────────────

@Composable
fun RenameWorkspaceDialog(
    workspace : WorkspaceEntity,
    onConfirm : (newName: String) -> Unit,
    onDismiss : () -> Unit,
) {
    var name by remember { mutableStateOf(workspace.name) }

    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        title = { Text("Rename Workspace", fontWeight = FontWeight.Bold) },
        text  = {
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text("Name") },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Color(0xFF9C6FFF),
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedLabelColor    = Color(0xFF9C6FFF),
                    unfocusedLabelColor  = Color(0xFF888888),
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    cursorColor          = Color(0xFF9C6FFF),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) {
                Text("Rename", color = Color(0xFF9C6FFF), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF888888))
            }
        },
    )
}

// ── Delete confirm dialog ─────────────────────────────────────────────────

@Composable
fun DeleteWorkspaceDialog(
    workspace : WorkspaceEntity,
    onConfirm : () -> Unit,
    onDismiss : () -> Unit,
) {
    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor  = Color(0xFFCCCCCC),
        title = { Text("Delete \"${workspace.name}\"?", fontWeight = FontWeight.Bold) },
        text  = {
            Text(
                "All tabs, cookies, logins, and storage for this workspace will be permanently deleted. This cannot be undone.",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = Color(0xFFCF6679), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF888888))
            }
        },
    )
}

// ── Clear data confirm dialog ─────────────────────────────────────────────

@Composable
fun ClearWorkspaceDataDialog(
    workspace : WorkspaceEntity,
    onConfirm : () -> Unit,
    onDismiss : () -> Unit,
) {
    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor  = Color(0xFFCCCCCC),
        title = { Text("Clear \"${workspace.name}\" data?", fontWeight = FontWeight.Bold) },
        text  = {
            Text(
                "Cookies, logins, localStorage, and cache for this workspace will be cleared. The workspace itself and its tabs will be kept.",
                fontSize = 14.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF888888))
            }
        },
    )
}
