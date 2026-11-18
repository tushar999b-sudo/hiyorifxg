package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.agent.tool.ToolCallRequest
import com.example.ui.theme.HiyoriThemeTokens

@Composable
fun PermissionDialog(
    request: ToolCallRequest,
    summaryText: String? = null,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    val colors = HiyoriThemeTokens.colors
    val targetDisplay = summaryText?.takeIf { it.isNotBlank() } ?: run {
        val path = request.parameters["path"]?.toString()
        val query = request.parameters["query"]?.toString()
            ?: request.parameters["text"]?.toString()
            ?: request.parameters["content"]?.toString()
        when {
            !path.isNullOrBlank() -> "Path: $path"
            !query.isNullOrBlank() -> "Memory: $query"
            else -> request.parameters.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        }
    }

    Dialog(onDismissRequest = onDeny) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.background,
            border = BorderStroke(1.dp, colors.glassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(24.dp), clip = false)
                .testTag("permission_dialog")
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Confirm Deletion",
                        tint = colors.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Confirm Deletion",
                        color = colors.onBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Hiyori is about to permanently delete the following item (auto-cancels in 60s):",
                    color = colors.outline,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.glassSurface)
                        .border(1.dp, colors.glassBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text(
                            text = request.toolName,
                            color = colors.onBackground,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = targetDisplay,
                            color = colors.onBackground,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDeny,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, colors.glassBorder),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("deny_tool_button")
                    ) {
                        Text("Deny", color = colors.outline)
                    }

                    Button(
                        onClick = onApprove,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("approve_tool_button")
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
