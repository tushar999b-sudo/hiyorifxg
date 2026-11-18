package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.avatar.HiyoriExpression
import com.example.ui.HiyoriUiState
import com.example.ui.components.AvatarViewport
import com.example.ui.theme.HiyoriThemeTokens

@Composable
fun AgentHubScreen(
    uiState: HiyoriUiState,
    onSendMessage: (String) -> Unit,
    onPokeAvatar: () -> Unit,
    onSelectExpression: (HiyoriExpression) -> Unit,
    onToggleVoiceMute: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onDraftMoodPreview: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Upper Scrollable Centerpiece
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AvatarViewport(
                expression = uiState.currentExpression,
                isSpeaking = uiState.isSpeaking,
                isThinking = uiState.isThinking,
                isListening = uiState.isListening,
                onPoke = onPokeAvatar,
                onSelectExpression = onSelectExpression
            )

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "How can I help you today?",
                color = colors.onBackground,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionChip(
                    icon = Icons.Outlined.Visibility,
                    label = "Inspect Mobile Screen",
                    onClick = { onSendMessage("Inspect current mobile screen") },
                    testTag = "quick_chip_inspect"
                )
                QuickActionChip(
                    icon = Icons.Outlined.PhoneAndroid,
                    label = "Open WhatsApp",
                    onClick = { onSendMessage("Open WhatsApp") },
                    testTag = "quick_chip_whatsapp"
                )
                QuickActionChip(
                    icon = Icons.Outlined.CleaningServices,
                    label = "Optimize",
                    onClick = { onSendMessage("Optimize device performance and RAM") },
                    testTag = "quick_chip_optimize"
                )
            }
        }

        // Bottom Glass Pill Input Bar + Remaining Actions Caption
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PillPromptInputBar(
                inputText = inputText,
                isListening = uiState.isListening,
                onTextChange = {
                    inputText = it
                    onDraftMoodPreview(it)
                },
                onSend = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                onAttachClick = { onSendMessage("Inspect current mobile screen") },
                onImageClick = { onSendMessage("Open Camera for vision") },
                onMicToggle = {
                    if (uiState.isListening) onStopVoice() else onStartVoice()
                },
                inputTestTag = "agent_hub_input",
                sendTestTag = "agent_hub_send_btn"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${uiState.remainingActions} agent actions left",
                color = colors.outline,
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = HiyoriThemeTokens.colors
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.glassSurface,
        border = BorderStroke(1.dp, colors.glassBorder),
        modifier = Modifier
            .height(38.dp)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = colors.onBackground,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = colors.onBackground,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PillPromptInputBar(
    inputText: String,
    isListening: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onImageClick: () -> Unit,
    onMicToggle: () -> Unit,
    inputTestTag: String,
    sendTestTag: String,
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors

    Surface(
        shape = RoundedCornerShape(50),
        color = colors.glassSurface,
        border = BorderStroke(1.dp, colors.glassBorder),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Attach",
                    tint = colors.outline,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onImageClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "Image",
                    tint = colors.outline,
                    modifier = Modifier.size(19.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = if (isListening) "Listening..." else "Ask Hiyori anything...",
                        color = colors.outline,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = colors.onBackground,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(inputTestTag)
                )
            }

            IconButton(
                onClick = onMicToggle,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    contentDescription = "Voice",
                    tint = if (isListening) colors.primary else colors.outline,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Solid circle send button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank()) colors.primary else colors.surfaceVariant
                    )
                    .clickable(enabled = inputText.isNotBlank()) { onSend() }
                    .testTag(sendTestTag),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank()) colors.onPrimary else colors.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
