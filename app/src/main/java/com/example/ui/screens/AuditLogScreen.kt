package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuditLogEntity
import com.example.ui.theme.HiyoriThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AuditLogScreen(
    logs: List<AuditLogEntity>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security",
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Security Audit Trail",
                        color = colors.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Permission checks & mobile agent execution log",
                    color = colors.outline,
                    fontSize = 12.sp
                )
            }

            if (logs.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearLogs,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier.testTag("clear_audit_logs_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear",
                        modifier = Modifier.size(15.dp),
                        tint = colors.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", fontSize = 12.sp, color = colors.outline)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Security Matrix Legend (16.dp small glass card)
        Surface(
            color = colors.glassSurface,
            border = BorderStroke(1.dp, colors.glassBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(16.dp), clip = false)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Permission Matrix Levels",
                    color = colors.onBackground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LegendItem("L0: Read", 0.35f)
                    LegendItem("L1: Local", 0.50f)
                    LegendItem("L2: GUI/Action", 0.68f)
                    LegendItem("L3: Comms", 0.85f)
                    LegendItem("L4: Shell", 1.0f)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = colors.outline,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No audit events logged yet",
                        color = colors.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mobile actions & permission checks will be recorded here.",
                        color = colors.outline,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    AuditItemCard(log = log, timeStr = dateFormat.format(Date(log.timestamp)))
                }
            }
        }
    }
}

@Composable
fun LegendItem(label: String, dotAlpha: Float) {
    val colors = HiyoriThemeTokens.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = colors.primary.copy(alpha = dotAlpha),
            shape = CircleShape,
            modifier = Modifier.size(7.dp)
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = colors.outline, fontSize = 11.sp)
    }
}

@Composable
fun AuditItemCard(log: AuditLogEntity, timeStr: String) {
    val colors = HiyoriThemeTokens.colors
    val isHighRisk = log.permissionLevel >= 3
    val isSuccess = log.status == "SUCCESS" || log.status == "APPROVED"

    Surface(
        color = colors.glassSurface,
        border = BorderStroke(1.dp, colors.glassBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isHighRisk) colors.primary else colors.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, colors.glassBorder)
                    ) {
                        Text(
                            text = "L${log.permissionLevel}",
                            color = if (isHighRisk) colors.onPrimary else colors.onBackground,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.actionName,
                        color = colors.onBackground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    color = if (isSuccess) colors.primary else colors.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, colors.glassBorder)
                ) {
                    Text(
                        text = log.status,
                        color = if (isSuccess) colors.onPrimary else colors.onBackground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Target: ${log.target} • $timeStr",
                color = colors.outline,
                fontSize = 12.sp
            )

            if (!log.resultDetails.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = colors.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = log.resultDetails,
                        color = colors.onBackground,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}
