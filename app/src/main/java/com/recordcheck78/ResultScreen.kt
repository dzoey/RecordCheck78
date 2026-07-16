package com.recordcheck78

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Result screen — shows the identified record info and whether it exists on IA.
 * User can edit fields, save to donation list, or add to batch queue.
 */
@Composable
fun ResultScreen(
    checkResult: ArchiveCheckResult,
    viewModel: AppViewModel,
    onRetake: () -> Unit
) {
    val record = checkResult.record
    var showEditDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ─── Status Banner ──────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (checkResult.exists) Color(0xFF1B3A1B) else Color(0xFF3A1B1B)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (checkResult.error != null) {
                    Text(
                        "⚠️ Lookup Error",
                        color = Color(0xFFFFA726),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        checkResult.error,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                } else if (checkResult.exists) {
                    Text(
                        "✅ Already on Internet Archive",
                        color = Color(0xFF4CAF50),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "This record is already in the archive — no need to donate.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        "📦 Not Found — Can Donate!",
                        color = Color(0xFFFF6B6B),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "The Internet Archive doesn't have this record yet. Consider donating it.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Identified Record Info ─────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C2E)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Identified Record", color = Color(0xFF00E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                InfoRow("Catalog #", record.catalogNumber.ifBlank { "—" })
                InfoRow("Artist", record.artist.ifBlank { "—" })
                InfoRow("Title", record.title.ifBlank { "—" })
                InfoRow("Label", record.labelName.ifBlank { "—" })
                if (record.labelStyle.isNotBlank()) {
                    InfoRow("Label Style", record.labelStyle)
                }
                InfoRow("Search Query", checkResult.searchQueryUsed.ifBlank { "—" })

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Details & Re-check")
                }
            }
        }

        // ─── Archive Matches (if found) ─────────────
        if (checkResult.archiveItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C2E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Found on Internet Archive (${checkResult.archiveItems.size} matches)",
                        color = Color(0xFF00E5FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    checkResult.archiveItems.take(5).forEach { item ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            if (item.creator.isNotBlank()) {
                                Text(item.creator, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                            }
                            if (item.date.isNotBlank()) {
                                Text("Date: ${item.date}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                            Text(
                                "archive.org/details/${item.identifier}",
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }

        // ─── Raw OCR Text (collapsible) ─────────────
        if (record.rawOcrText.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C2E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Raw OCR Text", color = Color(0xFF00E5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        record.rawOcrText,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ─── Action Buttons ─────────────────────────
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Add to batch
            OutlinedButton(
                onClick = { viewModel.addToBatch() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Queue, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add to Queue")
            }

            // Save to donation list
            Button(
                onClick = { viewModel.saveToDonationList() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save to List", color = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📷 Scan Another Record")
        }
    }

    // ─── Edit Dialog ────────────────────────────────
    if (showEditDialog) {
        EditRecordDialog(
            record = record,
            onDismiss = { showEditDialog = false },
            onConfirm = { cat, artist, title, label ->
                viewModel.editAndRecheck(cat, artist, title, label)
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EditRecordDialog(
    record: Record,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var catalogNumber by remember { mutableStateOf(record.catalogNumber) }
    var artist by remember { mutableStateOf(record.artist) }
    var title by remember { mutableStateOf(record.title) }
    var labelName by remember { mutableStateOf(record.labelName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Record Details") },
        text = {
            Column {
                OutlinedTextField(
                    value = catalogNumber,
                    onValueChange = { catalogNumber = it },
                    label = { Text("Catalog Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = labelName,
                    onValueChange = { labelName = it },
                    label = { Text("Label (Victor, Columbia, etc.)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(catalogNumber, artist, title, labelName) }) {
                Text("Re-check Archive")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}