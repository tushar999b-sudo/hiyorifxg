package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeMute
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.tool.PermissionLevel
import com.example.agent.tool.ToolCallRequest
import com.example.shizuku.ShizukuConfirmationGate
import com.example.shizuku.ShizukuManager
import com.example.ui.components.PermissionDialog
import com.example.ui.screens.AgentHubScreen
import com.example.ui.screens.AuditLogScreen
import com.example.ui.screens.CompanionScreen
import com.example.ui.screens.LiveAgentScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.PcBridgeScreen
import com.example.ui.screens.PermissionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.HiyoriThemeTokens

private enum class SecondaryRoute {
    NONE,
    SETTINGS,
    PERMISSIONS,
    LIVE_AGENT,
    AUDIT_LOG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiyoriMainView(
    viewModel: HiyoriViewModel,
    isDarkTheme: Boolean
) {
    val colors = HiyoriThemeTokens.colors
    val uiState by viewModel.uiState.collectAsState()
    val loopState by viewModel.loopState.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()
    val pcStatus by viewModel.pcStatus.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    val shizukuStatus by ShizukuManager.status.collectAsState()
    val shizukuPending by ShizukuConfirmationGate.pending.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var secondaryRoute by remember { mutableStateOf(SecondaryRoute.NONE) }
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingMicAction?.invoke()
        }
        pendingMicAction = null
    }

    fun runWithMicPermission(action: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingMicAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.notificationMessage) {
        uiState.notificationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissNotification()
        }
    }

    BackHandler(enabled = secondaryRoute != SecondaryRoute.NONE) {
        secondaryRoute = SecondaryRoute.NONE
    }

    BackHandler(enabled = secondaryRoute == SecondaryRoute.NONE && uiState.activeTab != CompanionTab.AGENT_HUB) {
        viewModel.selectTab(CompanionTab.AGENT_HUB)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (secondaryRoute == SecondaryRoute.NONE) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.onBackground)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HIYORI",
                                color = colors.onBackground,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = colors.surfaceVariant,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, colors.glassBorder),
                                modifier = Modifier.clickable { secondaryRoute = SecondaryRoute.LIVE_AGENT }
                            ) {
                                Text(
                                    text = if (loopState.running) "REACT LIVE" else "MOBILE AGENT",
                                    color = colors.onBackground,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.6.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        Surface(
                            color = colors.glassSurface,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = "Performance",
                                    tint = colors.onBackground,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${uiState.deviceStats.energyScore}/100",
                                    color = colors.onBackground,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.toggleVoiceMute() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("sound_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (uiState.voiceMuted) {
                                    Icons.AutoMirrored.Outlined.VolumeMute
                                } else {
                                    Icons.AutoMirrored.Outlined.VolumeUp
                                },
                                contentDescription = "Sound Toggle",
                                tint = if (uiState.voiceMuted) colors.outline else colors.onBackground,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.toggleTheme(isDarkTheme) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("theme_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) {
                                    Icons.Outlined.LightMode
                                } else {
                                    Icons.Outlined.DarkMode
                                },
                                contentDescription = "Toggle Theme",
                                tint = colors.onBackground,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = { secondaryRoute = SecondaryRoute.SETTINGS },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Agent Settings",
                                tint = colors.onBackground,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        titleContentColor = colors.onBackground
                    )
                )
            }
        },
        bottomBar = {
            if (secondaryRoute == SecondaryRoute.NONE) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Surface(
                        color = colors.glassSurface,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, colors.glassBorder),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("main_navigation_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompanionTab.entries.forEach { tab ->
                                val isSelected = uiState.activeTab == tab
                                val (icon, label) = when (tab) {
                                    CompanionTab.OVERVIEW -> Icons.Outlined.Home to "Overview"
                                    CompanionTab.AGENT_HUB -> Icons.Outlined.SmartToy to "Agent Hub"
                                    CompanionTab.CONTROLS -> Icons.Outlined.Tune to "Controls"
                                    CompanionTab.CHAT -> Icons.Outlined.ChatBubbleOutline to "AI Chat"
                                    CompanionTab.MEMORY -> Icons.Outlined.Psychology to "Memory"
                                }

                                Surface(
                                    color = if (isSelected) colors.primary else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(50))
                                        .clickable { viewModel.selectTab(tab) }
                                        .testTag("nav_${tab.name.lowercase()}")
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = if (isSelected) colors.onPrimary else colors.outline,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Spacer(modifier = Modifier.size(2.dp))
                                        Text(
                                            text = label,
                                            color = if (isSelected) colors.onPrimary else colors.outline,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Voice status line under header
                if (secondaryRoute == SecondaryRoute.NONE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.voiceStatus.label,
                            color = colors.outline,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("header_voice_status")
                        )
                        if (!uiState.wakeWordAvailable) {
                            Text(
                                text = "Tap the mic to talk",
                                color = colors.outline,
                                fontSize = 11.sp,
                                modifier = Modifier.testTag("wake_word_missing_hint")
                            )
                        }
                    }
                }

                // Live Autonomous Agent Banner with STOP Button
                AnimatedVisibility(visible = loopState.running && secondaryRoute != SecondaryRoute.LIVE_AGENT) {
                    Surface(
                        color = colors.glassSurface,
                        border = BorderStroke(1.dp, colors.glassBorder),
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { secondaryRoute = SecondaryRoute.LIVE_AGENT }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Step ${loopState.currentStep}/${loopState.maxSteps} • ${loopState.mood.title}",
                                    color = colors.outline,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = loopState.plan,
                                    color = colors.onBackground,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { viewModel.stop() },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                ),
                                modifier = Modifier.testTag("banner_stop_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.StopCircle,
                                    contentDescription = "STOP",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("STOP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (secondaryRoute) {
                        SecondaryRoute.SETTINGS -> {
                            SettingsScreen(
                                settings = settingsState,
                                geminiTestStatus = uiState.geminiTestStatus,
                                isTestingGemini = uiState.isTestingGemini,
                                lastGeminiHttpCode = uiState.lastGeminiHttpCode,
                                localTestStatus = uiState.localTestStatus,
                                isTestingLocal = uiState.isTestingLocal,
                                lastLocalHttpCode = uiState.lastLocalHttpCode,
                                whisperDownloadProgress = uiState.whisperDownloadProgress,
                                whisperStatusText = uiState.whisperStatusText,
                                onBack = { secondaryRoute = SecondaryRoute.NONE },
                                onToggleUseGeminiApi = { viewModel.setUseGeminiApi(it) },
                                onSaveGeminiKey = { viewModel.saveGeminiApiKey(it) },
                                onClearGeminiKey = { viewModel.clearGeminiApiKey() },
                                onTestGeminiConnection = { viewModel.testGeminiConnection() },
                                onToggleUseLocalModel = { viewModel.setUseLocalModel(it) },
                                onSaveLaptopAddress = { viewModel.saveLaptopAddress(it) },
                                onSelectLocalModel = { viewModel.setLocalModelName(it) },
                                onTestLocalConnection = { viewModel.testLocalConnection(it) },
                                onSavePorcupineKey = { viewModel.savePorcupineAccessKey(it) },
                                onClearPorcupineKey = { viewModel.clearPorcupineAccessKey() },
                                onDownloadWhisperModel = { viewModel.downloadWhisperModel() },
                                onToggleConfirmDeleteFiles = { viewModel.setConfirmDeleteFiles(it) },
                                onToggleConfirmCriticalSettings = { viewModel.setConfirmCriticalSettings(it) },
                                onToggleConfirmNewContacts = { viewModel.setConfirmNewContacts(it) },
                                onUpdateMaxSteps = { viewModel.setMaxAgentSteps(it) }
                            )
                        }
                        SecondaryRoute.PERMISSIONS -> {
                            PermissionsScreen(
                                hasMediaProjection = false,
                                onMediaProjectionGranted = { _, _ -> },
                                onBack = { secondaryRoute = SecondaryRoute.NONE }
                            )
                        }
                        SecondaryRoute.LIVE_AGENT -> {
                            LiveAgentScreen(
                                loopState = loopState,
                                onStartGoal = { viewModel.send(it) },
                                onStopImmediately = { viewModel.stop() },
                                onBack = { secondaryRoute = SecondaryRoute.NONE }
                            )
                        }
                        SecondaryRoute.AUDIT_LOG -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.background)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    IconButton(onClick = { secondaryRoute = SecondaryRoute.NONE }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                            contentDescription = "Back",
                                            tint = colors.onBackground
                                        )
                                    }
                                    Text(
                                        text = "Back to Controls",
                                        color = colors.onBackground,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                AuditLogScreen(
                                    logs = auditLogs,
                                    onClearLogs = { viewModel.clearAuditLogs() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        SecondaryRoute.NONE -> {
                            AnimatedContent(
                                targetState = uiState.activeTab,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                                },
                                label = "tab_content_transition"
                            ) { activeTab ->
                                when (activeTab) {
                                    CompanionTab.OVERVIEW -> {
                                        OverviewScreen(
                                            stats = uiState.deviceStats,
                                            expression = uiState.currentExpression,
                                            isLiveVoiceEnabled = settingsState.isLiveVoiceEnabled,
                                            onToggleLiveVoice = { enabled ->
                                                if (enabled) {
                                                    runWithMicPermission {
                                                        viewModel.setLiveVoiceEnabled(true)
                                                    }
                                                } else {
                                                    viewModel.setLiveVoiceEnabled(false)
                                                }
                                            },
                                            onOptimize = { viewModel.optimizeDevice() },
                                            onInspectScreen = { viewModel.inspectScreen() },
                                            onOpenWhatsApp = { viewModel.triggerQuickAction("open_app", mapOf("app_name" to "WhatsApp")) },
                                            onNavigateToControls = { viewModel.selectTab(CompanionTab.CONTROLS) },
                                            shizukuStatus = shizukuStatus
                                        )
                                    }
                                    CompanionTab.AGENT_HUB -> {
                                        AgentHubScreen(
                                            uiState = uiState,
                                            onSendMessage = { viewModel.send(it) },
                                            onPokeAvatar = { viewModel.pokeAvatar() },
                                            onSelectExpression = { viewModel.setExpression(it) },
                                            onToggleVoiceMute = { viewModel.toggleVoiceMute() },
                                            onStartVoice = {
                                                runWithMicPermission {
                                                    viewModel.startVoiceListening()
                                                }
                                            },
                                            onStopVoice = { viewModel.stopVoiceListening() },
                                            onDraftMoodPreview = { viewModel.updateMoodFromDraft(it) }
                                        )
                                    }
                                    CompanionTab.CONTROLS -> {
                                        PcBridgeScreen(
                                            pcStatus = pcStatus,
                                            screenVisionPreview = uiState.screenVisionPreview,
                                            onConnect = { viewModel.connectPc(it) },
                                            onDisconnect = { viewModel.disconnectPc() },
                                            onToggleSimulated = { viewModel.toggleSimulatedBridge() },
                                            onTriggerAction = { name, params -> viewModel.triggerQuickAction(name, params) },
                                            onOpenLiveAgent = { secondaryRoute = SecondaryRoute.LIVE_AGENT },
                                            onOpenPermissions = { secondaryRoute = SecondaryRoute.PERMISSIONS },
                                            onOpenAuditLog = { secondaryRoute = SecondaryRoute.AUDIT_LOG }
                                        )
                                    }
                                    CompanionTab.CHAT -> {
                                        CompanionScreen(
                                            uiState = uiState,
                                            chatMessages = chatMessages,
                                            onSendMessage = { viewModel.send(it) },
                                            onToggleVoiceMute = { viewModel.toggleVoiceMute() },
                                            onStartVoice = {
                                                runWithMicPermission {
                                                    viewModel.startVoiceListening()
                                                }
                                            },
                                            onStopVoice = { viewModel.stopVoiceListening() },
                                            onDraftMoodPreview = { viewModel.updateMoodFromDraft(it) }
                                        )
                                    }
                                    CompanionTab.MEMORY -> {
                                        MemoryScreen(
                                            memories = memories,
                                            onSaveMemory = { content, cat -> viewModel.saveMemory(content, cat) },
                                            onEditMemory = { viewModel.editMemory(it) },
                                            onDeleteMemory = { viewModel.deleteMemory(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            uiState.pendingPermissionRequest?.let { request ->
                PermissionDialog(
                    request = request,
                    onApprove = { viewModel.approvePendingTool() },
                    onDeny = { viewModel.denyPendingTool() }
                )
            }

            pendingConfirmation?.let { pending ->
                PermissionDialog(
                    request = ToolCallRequest(
                        callId = pending.callId,
                        toolName = pending.toolName,
                        parameters = mapOf("details" to pending.argumentsRedacted),
                        permissionLevel = PermissionLevel.fromInt(pending.permissionLevel)
                    ),
                    summaryText = pending.summary,
                    onApprove = { viewModel.approveConfirmation() },
                    onDeny = { viewModel.rejectConfirmation() }
                )
            }

            shizukuPending?.let { pending ->
                PermissionDialog(
                    request = ToolCallRequest(
                        callId = pending.id,
                        toolName = pending.toolName,
                        parameters = mapOf("phone" to pending.phone, "text" to pending.text),
                        permissionLevel = PermissionLevel.LEVEL_3
                    ),
                    summaryText = "Number: ${pending.phone}\nText: ${pending.text}",
                    onApprove = { ShizukuConfirmationGate.approve() },
                    onDeny = { ShizukuConfirmationGate.deny() }
                )
            }
        }
    }
}
