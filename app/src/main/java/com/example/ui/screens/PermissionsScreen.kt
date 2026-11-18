package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.permissions.HiyoriPermissionManager
import com.example.permissions.PermissionStatusLabel
import com.example.permissions.RuntimePermissionItem
import com.example.permissions.SpecialPermissionType
import com.example.ui.theme.HiyoriThemeTokens

@Composable
fun PermissionsScreen(
    hasMediaProjection: Boolean = false,
    onMediaProjectionGranted: (Int, Intent) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = HiyoriThemeTokens.colors
    var refreshTick by remember { mutableIntStateOf(0) }
    var pendingSettingsQueue by remember { mutableStateOf<List<SpecialPermissionType>>(emptyList()) }

    val singleRuntimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshTick++
    }

    // Refresh status on every ON_RESUME and advance sequential "Open all required settings" queue
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
                if (pendingSettingsQueue.isNotEmpty()) {
                    val remaining = pendingSettingsQueue.filter {
                        !HiyoriPermissionManager.isSpecialPermissionGranted(context, it)
                    }
                    if (remaining.isNotEmpty()) {
                        val next = remaining.first()
                        pendingSettingsQueue = remaining.drop(1)
                        HiyoriPermissionManager.openSpecialSettings(context, next)
                    } else {
                        pendingSettingsQueue = emptyList()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val runtimeItems = HiyoriPermissionManager.allRuntimePermissions
    val specialItems = SpecialPermissionType.entries

    val runtimeGrantedCount = remember(refreshTick) {
        runtimeItems.count { HiyoriPermissionManager.isRuntimePermissionGranted(context, it) }
    }
    val specialGrantedCount = remember(refreshTick) {
        specialItems.count { HiyoriPermissionManager.isSpecialPermissionGranted(context, it) }
    }
    val ungrantedSpecials = remember(refreshTick) {
        specialItems.filter { !HiyoriPermissionManager.isSpecialPermissionGranted(context, it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("permissions_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Tools & Permissions",
                        color = colors.onBackground,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Runtime ($runtimeGrantedCount/${runtimeItems.size}) • Special ($specialGrantedCount/${specialItems.size})",
                        color = colors.outline,
                        fontSize = 12.5.sp
                    )
                }
            }
        }

        // Top Card: Open all required settings sequentially
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.glassSurface,
            border = BorderStroke(1.dp, colors.glassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(24.dp), clip = false)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Special System Settings",
                            color = colors.onBackground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (ungrantedSpecials.isEmpty()) {
                                "All ${specialItems.size} special permissions are granted."
                            } else {
                                "${ungrantedSpecials.size} special permission(s) need Settings approval."
                            },
                            color = colors.outline,
                            fontSize = 12.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        val missing = specialItems.filter {
                            !HiyoriPermissionManager.isSpecialPermissionGranted(context, it)
                        }
                        if (missing.isNotEmpty()) {
                            val first = missing.first()
                            pendingSettingsQueue = missing.drop(1)
                            HiyoriPermissionManager.openSpecialSettings(context, first)
                        }
                    },
                    enabled = ungrantedSpecials.isNotEmpty(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_all_required_settings_btn")
                ) {
                    Text(
                        text = if (ungrantedSpecials.isEmpty()) {
                            "All Required Settings Granted"
                        } else {
                            "Open all required settings (${ungrantedSpecials.size})"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Section: Special Permissions
        Text(
            text = "SPECIAL PERMISSIONS",
            color = colors.outline,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 4.dp)
        )

        specialItems.forEach { special ->
            val status = remember(refreshTick, special) {
                HiyoriPermissionManager.getSpecialStatus(context, special)
            }
            PermissionEntryCard(
                icon = specialIcon(special),
                title = special.title,
                subtitle = special.subtitle,
                status = status,
                buttonLabel = "Open Settings",
                testTag = "perm_special_${special.name.lowercase()}",
                onAction = {
                    pendingSettingsQueue = emptyList()
                    HiyoriPermissionManager.openSpecialSettings(context, special)
                }
            )
        }

        // Section: Runtime Permissions
        Text(
            text = "RUNTIME PERMISSIONS",
            color = colors.outline,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        runtimeItems.forEach { item ->
            val status = remember(refreshTick, item.id) {
                HiyoriPermissionManager.getRuntimeStatus(context, item)
            }
            PermissionEntryCard(
                icon = runtimeIcon(item),
                title = item.title,
                subtitle = item.subtitle,
                status = status,
                buttonLabel = "Allow",
                testTag = "perm_runtime_${item.id}",
                onAction = {
                    if (item.isApplicableOnCurrentSdk()) {
                        singleRuntimeLauncher.launch(item.permission)
                    } else {
                        refreshTick++
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionEntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: PermissionStatusLabel,
    buttonLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    val colors = HiyoriThemeTokens.colors
    val isGranted = status == PermissionStatusLabel.GRANTED

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.glassSurface,
        border = BorderStroke(
            1.dp,
            if (isGranted) colors.primary.copy(alpha = 0.45f) else colors.glassBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isGranted) colors.primary else colors.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isGranted) colors.onPrimary else colors.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = colors.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = colors.outline,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = status.text,
                        color = if (isGranted) colors.onBackground else colors.outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (isGranted) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.primary
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Granted",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Granted",
                            color = colors.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.primary,
                    modifier = Modifier
                        .clickable { onAction() }
                        .testTag("${testTag}_btn")
                ) {
                    Text(
                        text = buttonLabel,
                        color = colors.onPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

private fun specialIcon(type: SpecialPermissionType): ImageVector = when (type) {
    SpecialPermissionType.ACCESSIBILITY -> Icons.Outlined.AccessibilityNew
    SpecialPermissionType.NOTIFICATION_LISTENER -> Icons.Outlined.NotificationsActive
    SpecialPermissionType.ALL_FILES -> Icons.Outlined.FolderOpen
    SpecialPermissionType.WRITE_SETTINGS -> Icons.Outlined.Tune
    SpecialPermissionType.DND_ACCESS -> Icons.Outlined.DoNotDisturbOn
    SpecialPermissionType.OVERLAY -> Icons.Outlined.Layers
    SpecialPermissionType.USAGE_ACCESS -> Icons.Outlined.QueryStats
    SpecialPermissionType.BATTERY_OPTIMIZATION -> Icons.Outlined.BatteryChargingFull
    SpecialPermissionType.EXACT_ALARMS -> Icons.Outlined.Alarm
    SpecialPermissionType.INSTALL_UNKNOWN_APPS -> Icons.Outlined.InstallMobile
}

private fun runtimeIcon(item: RuntimePermissionItem): ImageVector = when (item.id) {
    "call_phone" -> Icons.Outlined.Call
    "read_contacts" -> Icons.Outlined.Contacts
    "send_sms", "read_sms" -> Icons.Outlined.Sms
    "read_phone_state" -> Icons.Outlined.PhoneAndroid
    "camera" -> Icons.Outlined.CameraAlt
    "record_audio" -> Icons.Outlined.Mic
    "post_notifications" -> Icons.Outlined.NotificationsActive
    "read_media_images", "read_media_video", "read_media_audio", "read_external_storage" -> Icons.Outlined.PermMedia
    "access_fine_location", "access_coarse_location" -> Icons.Outlined.LocationOn
    "read_calendar", "write_calendar" -> Icons.Outlined.CalendarMonth
    "bluetooth_connect", "bluetooth_scan" -> Icons.Outlined.Bluetooth
    "nearby_wifi_devices" -> Icons.Outlined.Wifi
    else -> Icons.Outlined.Security
}
