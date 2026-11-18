package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.loop.AgentLoopState
import com.example.data.model.ActionStatus
import com.example.ui.theme.HiyoriThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LiveAgentScreen(
    loopState: AgentLoopState,
    onStartGoal: (String) -> Unit,
    onStopImmediately: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors
    var goalInput by remember { mutableStateOf("") }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val sampleGoals = listOf(
        "Remember that I prefer dark mode at night",
        "Search my memories for preferences",
        "Check battery, RAM, and storage",
        "Open WhatsApp and check messages"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
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
                    modifier = Modifier.testTag("live_agent_back_btn")
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
                        text = "Live ReAct Agent",
                        color = colors.onBackground,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Observe → Think → Plan → Act → Verify",
                        color = colors.outline,
                        fontSize = 12.sp
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = if (loopState.running) colors.primary else colors.glassSurface,
                border = BorderStroke(1.dp, if (loopState.running) colors.primary else colors.glassBorder)
            ) {
                Text(
                    text = if (loopState.running) "Step ${loopState.currentStep}/${loopState.maxSteps}" else loopState.mood.title,
                    color = if (loopState.running) colors.onPrimary else colors.outline,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        // STOP BUTTON (Monochrome Solid Pill)
        Button(
            onClick = onStopImmediately,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (loopState.running) colors.primary else colors.surfaceVariant,
                contentColor = if (loopState.running) colors.onPrimary else colors.onBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("big_red_stop_btn")
        ) {
            Icon(
                imageVector = Icons.Outlined.StopCircle,
                contentDescription = "Stop Agent",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (loopState.running) "STOP AGENT IMMEDIATELY" else "EMERGENCY HALT / CLEAR QUEUE",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.6.sp
            )
        }

        // Goal Launcher Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = goalInput,
                onValueChange = { goalInput = it },
                placeholder = {
                    Text("Give Hiyori a multi-step goal...", color = colors.outline, fontSize = 13.sp)
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.onBackground,
                    unfocusedTextColor = colors.onBackground,
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.glassBorder,
                    focusedContainerColor = colors.glassSurface,
                    unfocusedContainerColor = colors.glassSurface
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .testTag("live_goal_input")
            )
            Button(
                onClick = {
                    if (goalInput.isNotBlank()) {
                        onStartGoal(goalInput.trim())
                        goalInput = ""
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(50.dp)
                    .testTag("run_goal_btn")
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = "Run Goal")
            }
        }

        // Sample Goal Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sampleGoals.forEach { sample ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.glassSurface,
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier.clickable { onStartGoal(sample) }
                ) {
                    Text(
                        text = sample,
                        color = colors.onBackground,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Current Plan & Last Action Card (Large 24.dp glass card)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.glassSurface,
            border = BorderStroke(1.dp, colors.glassBorder),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(24.dp), clip = false)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (loopState.running) "RUNNING • MOOD: ${loopState.mood.name}" else "STATUS: ${loopState.mood.name}",
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Plan: ${loopState.plan}",
                    color = colors.onBackground,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                )
                if (loopState.lastAction.isNotBlank()) {
                    Text(
                        text = "Last Action: ${loopState.lastAction}",
                        color = colors.outline,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                if (loopState.lastResult.isNotBlank()) {
                    Text(
                        text = "Last Result: ${loopState.lastResult.take(180)}",
                        color = colors.outline,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // Step-by-Step ReAct Log
        Text(
            text = "STEP LOG (${loopState.stepLogs.size})",
            color = colors.outline,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(loopState.stepLogs, key = { it.id }) { log ->
                val statusDotColor = when (log.status) {
                    ActionStatus.SUCCESS -> colors.primary
                    ActionStatus.NOT_IMPLEMENTED, ActionStatus.PENDING_APPROVAL -> colors.outline
                    else -> colors.surfaceVariant
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.glassSurface,
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(statusDotColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Step ${log.stepNumber} • ${log.toolName} (${log.status.name})",
                                    color = colors.onBackground,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "${log.durationMs}ms • ${timeFmt.format(Date(log.timestamp))}",
                                color = colors.outline,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Args: ${log.argumentsRedacted}",
                            color = colors.outline,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = log.resultSummary,
                            color = colors.onBackground,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}
