package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.avatar.HiyoriExpression
import com.example.shizuku.ShizukuManager
import com.example.shizuku.ShizukuStatus
import com.example.ui.MobileDeviceStats
import com.example.ui.theme.HiyoriThemeTokens

@Composable
fun OverviewScreen(
    stats: MobileDeviceStats,
    expression: HiyoriExpression,
    isLiveVoiceEnabled: Boolean = false,
    onToggleLiveVoice: (Boolean) -> Unit = {},
    onOptimize: () -> Unit,
    onInspectScreen: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onNavigateToControls: () -> Unit,
    shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = HiyoriThemeTokens.colors
    val animatedScore by animateFloatAsState(
        targetValue = (stats.energyScore.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 700),
        label = "device_performance_gauge"
    )

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
                text = "Hi Master,",
                color = colors.outline,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Hiyori controls your mobile",
                color = colors.onBackground,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            )
        }

        // Live Voice Assistant Switch Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.glassSurface,
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("overview_live_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(colors.surfaceVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = "Live Voice Assistant",
                            tint = colors.onBackground,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Live",
                            color = colors.onBackground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isLiveVoiceEnabled) "Wake word 'Hiyori' active" else "Hands-free wake word off",
                            color = colors.outline,
                            fontSize = 12.sp
                        )
                    }
                }
                Switch(
                    checked = isLiveVoiceEnabled,
                    onCheckedChange = onToggleLiveVoice,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.outline,
                        uncheckedTrackColor = colors.surfaceVariant
                    ),
                    modifier = Modifier.testTag("overview_live_switch")
                )
            }
        }

        // Large Glass Card (24dp corners)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.glassSurface,
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("overview_performance_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "DEVICE PERFORMANCE",
                    color = colors.outline,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier.size(148.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = colors.surfaceVariant
                    val activeColor = colors.primary
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 11.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                        val arcSize = Size(diameter, diameter)

                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        drawArc(
                            color = activeColor,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedScore,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${stats.energyScore}",
                            color = colors.onBackground,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = "/100",
                            color = colors.outline,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Three Small Stat Tiles (16dp corners)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OverviewStatTile(
                icon = Icons.Outlined.BatteryChargingFull,
                label = "Battery",
                value = "${stats.batteryPercent}%",
                sublabel = "normal",
                modifier = Modifier.weight(1f)
            )
            OverviewStatTile(
                icon = Icons.Outlined.Memory,
                label = "Memory",
                value = "${stats.ramFreeGb} GB",
                sublabel = "free",
                modifier = Modifier.weight(1f)
            )
            OverviewStatTile(
                icon = Icons.Outlined.Shield,
                label = "Security",
                value = stats.securityStatus,
                sublabel = "active",
                modifier = Modifier.weight(1f)
            )
        }

        ShizukuStatusCard(
            status = shizukuStatus,
            onAction = {
                when (shizukuStatus) {
                    ShizukuStatus.NOT_INSTALLED -> ShizukuManager.openInstallShizuku(context)
                    ShizukuStatus.NOT_RUNNING -> ShizukuManager.openShizukuApp(context)
                    ShizukuStatus.RUNNING_NO_PERMISSION -> ShizukuManager.requestPermission()
                    ShizukuStatus.PERMISSION_DENIED_PERMANENTLY -> ShizukuManager.openShizukuApp(context)
                    ShizukuStatus.VERSION_TOO_OLD -> ShizukuManager.openInstallShizuku(context)
                    ShizukuStatus.READY -> Unit
                }
            }
        )

        // Agentic Mobile Control Shortcuts (2-column grid, 16dp corners)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "AGENTIC MOBILE CONTROL SHORTCUTS",
                color = colors.outline,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShortcutCard(
                    icon = Icons.Outlined.CleaningServices,
                    title = "One-Tap Optimize",
                    subtitle = "Free RAM & cache",
                    onClick = onOptimize,
                    testTag = "shortcut_optimize",
                    modifier = Modifier.weight(1f)
                )
                ShortcutCard(
                    icon = Icons.Outlined.Visibility,
                    title = "Inspect Screen",
                    subtitle = "Read UI & OCR",
                    onClick = onInspectScreen,
                    testTag = "shortcut_inspect",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShortcutCard(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "Launch WhatsApp",
                    subtitle = "Quick messaging",
                    onClick = onOpenWhatsApp,
                    testTag = "shortcut_whatsapp",
                    modifier = Modifier.weight(1f)
                )
                ShortcutCard(
                    icon = Icons.Outlined.Tune,
                    title = "Mobile controls",
                    subtitle = "System & bridge",
                    onClick = onNavigateToControls,
                    testTag = "shortcut_controls",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun OverviewStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    sublabel: String,
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.glassSurface,
        border = BorderStroke(1.dp, colors.glassBorder),
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.onBackground,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = colors.outline,
                fontSize = 11.5.sp
            )
            Text(
                text = value,
                color = colors.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = sublabel,
                color = colors.outline,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ShortcutCard(
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
                    modifier = Modifier.size(20.dp)
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

@Composable
private fun ShizukuStatusCard(
    status: ShizukuStatus,
    onAction: () -> Unit
) {
    val colors = HiyoriThemeTokens.colors
    val (statusLabel, buttonLabel) = when (status) {
        ShizukuStatus.NOT_INSTALLED -> "Not installed" to "Install Shizuku"
        ShizukuStatus.NOT_RUNNING -> "Not running" to "Open Shizuku"
        ShizukuStatus.RUNNING_NO_PERMISSION -> "Permission needed" to "Grant permission"
        ShizukuStatus.READY -> "Ready" to null
        ShizukuStatus.PERMISSION_DENIED_PERMANENTLY -> "Permission denied" to "Open Shizuku to allow"
        ShizukuStatus.VERSION_TOO_OLD -> "Version too old" to "Update Shizuku"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.glassSurface,
        border = BorderStroke(1.dp, colors.glassBorder),
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shizuku_status_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(colors.surfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Terminal,
                        contentDescription = "Shizuku Shell",
                        tint = colors.onBackground,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Shizuku ADB Shell",
                        color = colors.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = statusLabel,
                        color = colors.outline,
                        fontSize = 12.sp
                    )
                }
            }

            if (buttonLabel != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    modifier = Modifier.testTag("shizuku_action_btn")
                ) {
                    Text(
                        text = buttonLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
