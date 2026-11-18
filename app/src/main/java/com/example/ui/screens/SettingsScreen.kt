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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.settings.HiyoriSettings
import com.example.ui.theme.HiyoriThemeTokens

@Composable
fun SettingsScreen(
    settings: HiyoriSettings,
    geminiTestStatus: String?,
    isTestingGemini: Boolean,
    lastGeminiHttpCode: Int?,
    localTestStatus: String?,
    isTestingLocal: Boolean,
    lastLocalHttpCode: Int?,
    whisperDownloadProgress: Float?,
    whisperStatusText: String,
    onBack: () -> Unit,
    onToggleUseGeminiApi: (Boolean) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onClearGeminiKey: () -> Unit,
    onTestGeminiConnection: () -> Unit,
    onToggleUseLocalModel: (Boolean) -> Unit,
    onSaveLaptopAddress: (String) -> Unit,
    onSelectLocalModel: (String) -> Unit,
    onTestLocalConnection: (String) -> Unit,
    onSavePorcupineKey: (String) -> Unit,
    onClearPorcupineKey: () -> Unit,
    onDownloadWhisperModel: () -> Unit,
    onToggleConfirmDeleteFiles: (Boolean) -> Unit,
    onToggleConfirmCriticalSettings: (Boolean) -> Unit,
    onToggleConfirmNewContacts: (Boolean) -> Unit,
    onUpdateMaxSteps: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors
    var geminiKeyInput by remember { mutableStateOf("") }
    var laptopAddressInput by remember { mutableStateOf(settings.laptopAddress) }
    var porcupineKeyInput by remember { mutableStateOf("") }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(settings.laptopAddress) {
        if (laptopAddressInput.isEmpty() && settings.laptopAddress.isNotEmpty()) {
            laptopAddressInput = settings.laptopAddress
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("settings_back_btn")
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
                    text = "Agent & LLM Settings",
                    color = colors.onBackground,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Gemini API, Local model, Voice & safety",
                    color = colors.outline,
                    fontSize = 12.5.sp
                )
            }
        }

        // BLOCK A: Gemini API
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.glassSurface,
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gemini_api_block")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = null,
                            tint = colors.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gemini API",
                            color = colors.onBackground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Use Gemini API",
                            color = colors.outline,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = settings.useGeminiApi,
                            onCheckedChange = onToggleUseGeminiApi,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onPrimary,
                                checkedTrackColor = colors.primary,
                                uncheckedThumbColor = colors.outline,
                                uncheckedTrackColor = colors.surfaceVariant
                            ),
                            modifier = Modifier.testTag("use_gemini_api_switch")
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (settings.useGeminiApi) 1f else 0.45f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (settings.cachedGeminiModel.isNotBlank()) {
                                "Model: ${settings.cachedGeminiModel}"
                            } else {
                                "Auto-selects Flash model"
                            },
                            color = colors.outline,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = colors.surfaceVariant
                        ) {
                            Text(
                                text = if (settings.hasGeminiApiKey) settings.maskedGeminiKey else "Not Set",
                                color = colors.onBackground,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = geminiKeyInput,
                        onValueChange = { geminiKeyInput = it },
                        enabled = settings.useGeminiApi,
                        label = { Text("Gemini API key", fontSize = 12.sp) },
                        placeholder = {
                            Text("Paste AIza... API key", color = colors.outline, fontSize = 12.5.sp)
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.onBackground,
                            unfocusedTextColor = colors.onBackground,
                            disabledTextColor = colors.outline,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.glassBorder,
                            disabledBorderColor = colors.glassBorder
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (geminiKeyInput.isNotBlank()) {
                                    onSaveGeminiKey(geminiKeyInput)
                                    geminiKeyInput = ""
                                }
                            },
                            enabled = settings.useGeminiApi,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary
                            ),
                            modifier = Modifier.testTag("save_gemini_key_btn")
                        ) {
                            Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        }

                        if (settings.hasGeminiApiKey) {
                            OutlinedButton(
                                onClick = onClearGeminiKey,
                                enabled = settings.useGeminiApi,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, colors.glassBorder),
                                modifier = Modifier.testTag("clear_gemini_key_btn")
                            ) {
                                Text("Clear", color = colors.outline, fontSize = 12.5.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = onTestGeminiConnection,
                            enabled = settings.useGeminiApi && !isTestingGemini,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier.testTag("test_gemini_connection_btn")
                        ) {
                            Text(
                                text = if (isTestingGemini) "Testing..." else "Test connection",
                                color = colors.onBackground,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (!geminiTestStatus.isNullOrBlank()) {
                        Text(
                            text = geminiTestStatus,
                            color = colors.outline,
                            fontSize = 11.5.sp,
                            modifier = Modifier.testTag("gemini_test_status")
                        )
                    }

                    if (lastGeminiHttpCode != null) {
                        Text(
                            text = "Last HTTP error: $lastGeminiHttpCode",
                            color = colors.outline,
                            fontSize = 11.sp,
                            modifier = Modifier.testTag("gemini_last_http_error")
                        )
                    }
                }
            }
        }

        // BLOCK B: Local model (laptop) — placed BELOW Gemini API
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.glassSurface,
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("local_model_block")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Laptop,
                            contentDescription = null,
                            tint = colors.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Local model (laptop)",
                            color = colors.onBackground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Use Local model",
                            color = colors.outline,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = settings.useLocalModel,
                            onCheckedChange = onToggleUseLocalModel,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onPrimary,
                                checkedTrackColor = colors.primary,
                                uncheckedThumbColor = colors.outline,
                                uncheckedTrackColor = colors.surfaceVariant
                            ),
                            modifier = Modifier.testTag("use_local_model_switch")
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (settings.useLocalModel) 1f else 0.45f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = laptopAddressInput,
                        onValueChange = { laptopAddressInput = it },
                        enabled = settings.useLocalModel,
                        label = { Text("Laptop address", fontSize = 12.sp) },
                        placeholder = {
                            Text("http://192.168.x.x:11434", color = colors.outline, fontSize = 12.5.sp)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.onBackground,
                            unfocusedTextColor = colors.onBackground,
                            disabledTextColor = colors.outline,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.glassBorder,
                            disabledBorderColor = colors.glassBorder
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("laptop_address_input")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Model name",
                            color = colors.outline,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = colors.surfaceVariant,
                                border = BorderStroke(1.dp, colors.glassBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = settings.useLocalModel) {
                                        modelDropdownExpanded = true
                                    }
                                    .testTag("local_model_dropdown")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = settings.localModelName,
                                        color = colors.onBackground,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowDropDown,
                                        contentDescription = "Select local model",
                                        tint = colors.onBackground
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false },
                                modifier = Modifier.background(colors.background)
                            ) {
                                HiyoriSettings.LOCAL_MODEL_OPTIONS.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option,
                                                color = colors.onBackground,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            modelDropdownExpanded = false
                                            onSelectLocalModel(option)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onSaveLaptopAddress(laptopAddressInput) },
                            enabled = settings.useLocalModel,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary
                            ),
                            modifier = Modifier.testTag("save_laptop_address_btn")
                        ) {
                            Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                        }

                        OutlinedButton(
                            onClick = { onTestLocalConnection(laptopAddressInput) },
                            enabled = settings.useLocalModel && !isTestingLocal,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier.testTag("test_local_connection_btn")
                        ) {
                            Text(
                                text = if (isTestingLocal) "Testing..." else "Test local connection",
                                color = colors.onBackground,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (!localTestStatus.isNullOrBlank()) {
                        Text(
                            text = localTestStatus,
                            color = colors.outline,
                            fontSize = 11.5.sp,
                            modifier = Modifier.testTag("local_test_status")
                        )
                    }

                    if (lastLocalHttpCode != null) {
                        Text(
                            text = "Last HTTP error: $lastLocalHttpCode",
                            color = colors.outline,
                            fontSize = 11.sp,
                            modifier = Modifier.testTag("local_last_http_error")
                        )
                    }
                }
            }
        }

        // BLOCK C: Voice & Wake Word
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.glassSurface,
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_settings_block")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.GraphicEq,
                            contentDescription = null,
                            tint = colors.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Voice & Wake Word",
                            color = colors.onBackground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = colors.surfaceVariant
                    ) {
                        Text(
                            text = if (settings.hasPorcupineKey) settings.maskedPorcupineKey else "Key Not Set",
                            color = colors.onBackground,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = porcupineKeyInput,
                    onValueChange = { porcupineKeyInput = it },
                    label = { Text("Porcupine AccessKey", fontSize = 12.sp) },
                    placeholder = {
                        Text("Paste Picovoice AccessKey", color = colors.outline, fontSize = 12.5.sp)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.onBackground,
                        unfocusedTextColor = colors.onBackground,
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.glassBorder
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("porcupine_key_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (porcupineKeyInput.isNotBlank()) {
                                onSavePorcupineKey(porcupineKeyInput)
                                porcupineKeyInput = ""
                            }
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        modifier = Modifier.testTag("save_porcupine_key_btn")
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                    }

                    if (settings.hasPorcupineKey) {
                        OutlinedButton(
                            onClick = onClearPorcupineKey,
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, colors.glassBorder),
                            modifier = Modifier.testTag("clear_porcupine_key_btn")
                        ) {
                            Text("Clear", color = colors.outline, fontSize = 12.5.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "On-Device Whisper STT",
                        color = colors.onBackground,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = whisperStatusText,
                        color = colors.outline,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("whisper_status_text")
                    )
                    if (whisperDownloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { whisperDownloadProgress },
                            color = colors.primary,
                            trackColor = colors.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = onDownloadWhisperModel,
                        enabled = whisperDownloadProgress == null,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, colors.glassBorder),
                        modifier = Modifier.testTag("download_whisper_model_btn")
                    ) {
                        Text(
                            text = if (settings.whisperModelDownloaded) "Re-download model" else "Download model",
                            color = colors.onBackground,
                            fontSize = 12.5.sp
                        )
                    }
                }

                Text(
                    text = "Live listening uses the microphone and reduces battery. Turn it off when you do not need it.",
                    color = colors.outline,
                    fontSize = 11.5.sp
                )
            }
        }

        // BLOCK D: Safety Switches & Max Steps (24dp large card)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.glassSurface,
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = colors.onBackground,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SAFETY SWITCHES (DEFAULT ON)",
                        color = colors.outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }

                SafetySwitchRow(
                    title = "Confirm before deleting files",
                    subtitle = "Ask Master before file.delete runs",
                    checked = settings.confirmDeleteFiles,
                    onCheckedChange = onToggleConfirmDeleteFiles
                )
                SafetySwitchRow(
                    title = "Confirm critical system settings",
                    subtitle = "Ask before toggling Wi-Fi, Bluetooth, DND",
                    checked = settings.confirmCriticalSettings,
                    onCheckedChange = onToggleConfirmCriticalSettings
                )
                SafetySwitchRow(
                    title = "Confirm messaging new contacts",
                    subtitle = "Verify recipient & text on first message",
                    checked = settings.confirmNewContacts,
                    onCheckedChange = onToggleConfirmNewContacts
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Max ReAct Loop Steps",
                            color = colors.onBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Limit autonomous steps per goal (max 3)",
                            color = colors.outline,
                            fontSize = 11.5.sp
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onUpdateMaxSteps((settings.maxAgentSteps - 1).coerceAtLeast(1)) },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, colors.glassBorder)
                        ) {
                            Text("-", color = colors.onBackground)
                        }
                        Text(
                            text = "${settings.maxAgentSteps.coerceAtMost(3)}",
                            color = colors.onBackground,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { onUpdateMaxSteps((settings.maxAgentSteps + 1).coerceAtMost(3)) },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, colors.glassBorder)
                        ) {
                            Text("+", color = colors.onBackground)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SafetySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = HiyoriThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = colors.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = colors.outline,
                fontSize = 11.5.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.outline,
                uncheckedTrackColor = colors.surfaceVariant
            )
        )
    }
}
