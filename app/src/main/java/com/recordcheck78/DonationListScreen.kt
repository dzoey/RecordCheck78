package com.recordcheck78

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Donation list screen — shows all scanned records with their donation status.
 * User can mark records as donated, remove them, or see IA match details.
 */
@Composable
fun DonationListScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val donationList by viewModel.donationList.collectAsState()
    val batchQueue by viewModel.batchQueue.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A1A))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Donation List",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onBack) { Text("← Back", color = Color(0xFF00E5FF)) }
        }

        // Stats bar
        val needsDonation = donationList.count { it.status == DonationStatus.NEEDS_DONATION }
        val alreadyExists = donationList.count { it.status == DonationStatus.ALREADY_EXISTS }
        val donated = donationList.count { it.status == DonationStatus.DONATED }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip("To Donate", needsDonation, Color(0xFFFF6B6B))
            StatChip("On IA", alreadyExists, Color(0xFF4CAF50))
            StatChip("Done", donated, Color(0xFF00E5FF))
        }

        // Batch queue commit button
        if (batchQueue.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.commitBatch() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
            ) {
                Text("Commit Batch (${batchQueue.size} records)", color = Color.Black)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // List
        if (donationList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(" vinyl", fontSize = 64.sp)
                    Text(
                        "No records scanned yet.\nPhotograph a 78rpm record to start!",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(donationList, key = { it.id }) { item ->
                    DonationCard(
                        item = item,
                        onMarkDonated = { viewModel.updateStatus(item.id, DonationStatus.DONATED) },
                        onRemove = { viewModel.removeItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, count: Int, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            "$label: $count",
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DonationCard(
    item: DonationListItem,
    onMarkDonated: () -> Unit,
    onRemove: () -> Unit
) {
    val statusColor = when (item.status) {
        DonationStatus.NEEDS_DONATION -> Color(0xFFFF6B6B)
        DonationStatus.ALREADY_EXISTS -> Color(0xFF4CAF50)
        DonationStatus.DONATED -> Color(0xFF00E5FF)
        DonationStatus.UPLOADED -> Color(0xFF9C27B0)
    }

    val statusText = when (item.status) {
        DonationStatus.NEEDS_DONATION -> "Needs Donation"
        DonationStatus.ALREADY_EXISTS -> "Already on IA"
        DonationStatus.DONATED -> "Donated"
        DonationStatus.UPLOADED -> "Uploaded"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C2E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.record.title.ifBlank { "Unknown title" },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        item.record.artist.ifBlank { "Unknown artist" },
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                    if (item.record.catalogNumber.isNotBlank()) {
                        Text(
                            "Catalog: ${item.record.catalogNumber}",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                    if (item.record.labelName.isNotBlank()) {
                        Text(
                            item.record.labelName,
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp
                        )
                    }
                }
                Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // IA match details if available
            if (item.checkResult?.archiveItems?.isNotEmpty() == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "IA: ${item.checkResult.archiveItems.first().title}",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp
                )
            }

            // Action buttons
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.status == DonationStatus.NEEDS_DONATION) {
                    OutlinedButton(
                        onClick = onMarkDonated,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mark Donated", fontSize = 12.sp)
                    }
                }
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Red)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", color = Color.Red, fontSize = 12.sp)
                }
            }
        }
    }
}