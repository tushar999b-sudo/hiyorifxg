package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.tool.BridgeState
import com.example.agent.tool.PcBridgeStatus
import com.example.ui.theme.HiyoriThemeTokens

@Composable
fun PcBridgeScreen(
    pcStatus: PcBridgeStatus,
    screenVisionPreview: Map<String, Any>?,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onToggleSimulated: () -> Unit,
    onTriggerAction: (String, Map<String, Any>) -> Unit,
    onOpenLiveAgent: () -> Unit = {},
    onOpenPermissions: () -> Unit = {},
    onOpenAuditLog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text(
                text = "Mobile Controls",
                color = colors.onBackground,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Perception, app dispatchers & PC bridge",
                color = colors.outline,
                fontSize = 13.5.sp
            )
        }

        // 1. Mobile Screen Perception (24dp large card)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "MOBILE SCREEN PERCEPTION",
                color = colors.outline,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.glassSurface,
                border = BorderStroke(1.dp, colors.glassBorder),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("controls_screen_perception_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(colors.surfaceVariant, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Visibility,
                                    contentDescription = "Screen Perception",
                                    tint = colors.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Mobile Screen Perception",
                                    color = colors.onBackground,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Live status • 1080x2400",
                                    color = colors.outline,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = colors.primary,
                            modifier = Modifier
                                .clickable { onTriggerAction("mobile.screen_inspect", emptyMap()) }
                                .testTag("inspect_now_btn")
                        ) {
                            Text(
                                text = "Inspect Now",
                                color = colors.onPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    if (screenVisionPreview != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "OCR & UI Tree Snapshot",
                                    color = colors.onBackground,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = screenVisionPreview.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                                    color = colors.onBackground,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Mobile App Dispatchers (2x2 Grid, 16dp small cards)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "MOBILE APP DISPATCHERS",
                color = colors.outline,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DispatcherGridCard(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "WhatsApp Dispatch",
                    subtitle = "Send & read chats",
                    onClick = { onTriggerAction("mobile.open_app", mapOf("app_name" to "WhatsApp")) },
                    testTag = "dispatch_whatsapp",
                    modifier = Modifier.weight(1f)
                )
                DispatcherGridCard(
                    icon = Icons.Outlined.CameraAlt,
                    title = "Camera Vision",
                    subtitle = "Visual scene capture",
                    onClick = { onTriggerAction("mobile.open_app", mapOf("app_name" to "Camera")) },
                    testTag = "dispatch_camera",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DispatcherGridCard(
                    icon = Icons.Outlined.CleaningServices,
                    title = "Clean & Optimize",
                    subtitle = "Boost RAM & cache",
                    onClick = { onTriggerAction("mobile.optimize_device", emptyMap()) },
                    testTag = "dispatch_optimize",
                    modifier = Modifier.weight(1f)
                )
                DispatcherGridCard(
                    icon = Icons.Outlined.Settings,
                    title = "Device Settings",
                    subtitle = "Wi-Fi & system",
                    onClick = { onTriggerAction("mobile.toggle_setting", mapOf("setting" to "wifi")) },
                    testTag = "dispatch_settings",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. PC Agent Bridge Card (24dp large card)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "PC AGENT BRIDGE",
                color = colors.outline,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            val bridgeStatusLabel = when (pcStatus.state) {
                BridgeState.SIMULATED_READY -> "Ready / Simulated"
                BridgeState.CONNECTED -> "Connected • ${pcStatus.hostAddress}"
                BridgeState.CONNECTING -> "Connecting..."
                BridgeState.DISCONNECTED -> "Disconnected"
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.glassSurface,
                border = BorderStroke(1.dp, colors.glassBorder),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSimulated() }
                    .testTag("pc_bridge_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(colors.surfaceVariant, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Computer,
                                contentDescription = "PC Agent Bridge",
                                tint = colors.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PC Agent Bridge",
                                color = colors.onBackground,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Desktop companion relay",
                                color = colors.outline,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.surfaceVariant,
                        border = BorderStroke(1.dp, colors.glassBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.onBackground)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = bridgeStatusLabel,
                                color = colors.onBackground,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 4. Autonomous Agent & Permissions
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "AUTONOMOUS ENGINE & PERMISSIONS",
                color = colors.outline,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DispatcherGridCard(
                    icon = Icons.Outlined.Psychology,
                    title = "Live ReAct Agent",
                    subtitle = "Step trace & STOP",
                    onClick = onOpenLiveAgent,
                    testTag = "open_live_agent_card",
                    modifier = Modifier.weight(1f)
                )
                DispatcherGridCard(
                    icon = Icons.Outlined.Security,
                    title = "Tools & Permissions",
                    subtitle = "Accessibility & Files",
                    onClick = onOpenPermissions,
                    testTag = "open_permissions_card",
                    modifier = Modifier.weight(1f)
                )
            }

            DispatcherGridCard(
                icon = Icons.Outlined.History,
                title = "Action Audit Log",
                subtitle = "Timestamped history of every autonomous action",
                onClick = onOpenAuditLog,
                testTag = "open_audit_log_card",
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun DispatcherGridCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.glassSurface,
        border = BorderStroke(1.dp, colors.glassBorder),
        shadowElevation = 3.dp,
        modifier = modifier
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = colors.onBackground,
                    modifier = Modifier.size(19.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    color = colors.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = colors.outline,
                    fontSize = 12.sp
                )
            }
        }
    }
}
