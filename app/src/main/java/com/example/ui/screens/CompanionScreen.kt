package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.avatar.HiyoriExpression
import com.example.data.model.ChatMessageEntity
import com.example.data.model.MessageSender
import com.example.ui.HiyoriUiState
import com.example.ui.theme.HiyoriThemeTokens

@Composable
fun CompanionScreen(
    uiState: HiyoriUiState,
    chatMessages: List<ChatMessageEntity>,
    onSendMessage: (String) -> Unit,
    onToggleVoiceMute: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onDraftMoodPreview: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to newest message when a message is added or thinking state changes
    LaunchedEffect(chatMessages.size, uiState.isThinking) {
        val totalItems = chatMessages.size + if (uiState.isThinking) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Fixed-height Character Header (does not overlap messages)
        val currentExpr = uiState.currentExpression
        Surface(
            color = colors.glassSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(64.dp)
                .testTag("chat_live_mood_bar")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Crossfade(
                    targetState = currentExpr,
                    animationSpec = tween(durationMillis = 260),
                    label = "chat_avatar_mood"
                ) { expr ->
                    Image(
                        painter = painterResource(id = expr.baseDrawableRes),
                        contentDescription = "Hiyori ${expr.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.dp, colors.glassBorder, CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hiyori",
                            color = colors.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = colors.surfaceVariant,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = currentExpr.title,
                                color = colors.onBackground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = when {
                            uiState.isThinking -> "Sensing mood & thinking..."
                            uiState.isListening -> "Listening..."
                            uiState.isSpeaking -> "Speaking..."
                            else -> currentExpr.emotionNote
                        },
                        color = colors.outline,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Message List with Modifier.weight(1f)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            items(chatMessages, key = { it.id }) { msg ->
                ChatMessageItem(
                    msg = msg,
                    onActionChipClick = { actionPrompt -> onSendMessage(actionPrompt) }
                )
            }

            if (uiState.isThinking) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            color = colors.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hiyori is reasoning & planning",
                            color = colors.outline,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }

        // Glass Pill Input Bar with Modifier.imePadding()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
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
                onAttachClick = { onSendMessage("Inspect screen OCR") },
                onImageClick = { onSendMessage("Open Camera for vision") },
                onMicToggle = {
                    if (uiState.isListening) onStopVoice() else onStartVoice()
                },
                inputTestTag = "chat_input",
                sendTestTag = "chat_send_btn"
            )
        }
    }
}

@Composable
private fun ChatMessageItem(
    msg: ChatMessageEntity,
    onActionChipClick: (String) -> Unit
) {
    val colors = HiyoriThemeTokens.colors
    val isUser = msg.sender == MessageSender.USER.name
    val isSystem = msg.sender == MessageSender.SYSTEM.name

    if (isSystem) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = colors.glassSurface,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, colors.glassBorder)
            ) {
                Text(
                    text = msg.content,
                    color = colors.outline,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            val expr = HiyoriExpression.fromString(msg.expression)
            Image(
                painter = painterResource(id = expr.baseDrawableRes),
                contentDescription = "Hiyori",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.dp, colors.glassBorder, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.84f)
        ) {
            // User bubble: solid #616161 in dark, #E0E0E0 in light; Hiyori bubble: glass card
            Surface(
                color = if (isUser) colors.userBubble else colors.glassSurface,
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 6.dp,
                    bottomEnd = if (isUser) 6.dp else 20.dp
                ),
                border = if (isUser) null else BorderStroke(1.dp, colors.glassBorder),
                shadowElevation = if (isUser) 2.dp else 4.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (!isUser) {
                        val expr = HiyoriExpression.fromString(msg.expression)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Hiyori",
                                color = colors.onBackground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = colors.surfaceVariant,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = expr.title,
                                    color = colors.onBackground,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text(
                        text = msg.content,
                        color = if (isUser) colors.onUserBubble else colors.onBackground,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            if (!isUser) {
                if (!msg.providerLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = msg.providerLabel,
                        color = colors.outline,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .testTag("reply_provider_label")
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (msg.toolStatus != null) {
                        Surface(
                            color = colors.glassSurface,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, colors.glassBorder)
                        ) {
                            Text(
                                text = msg.toolStatus,
                                color = colors.onBackground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("reply_tool_status")
                            )
                        }
                    } else if (msg.toolActionName != null) {
                        Surface(
                            color = colors.glassSurface,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, colors.glassBorder)
                        ) {
                            Text(
                                text = "${msg.toolActionName} • DONE",
                                color = colors.onBackground,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = colors.glassSurface,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier.clickable { onActionChipClick("Inspect screen OCR") }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Visibility,
                                    contentDescription = "Inspect screen OCR",
                                    tint = colors.onBackground,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Inspect screen OCR",
                                    color = colors.outline,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
