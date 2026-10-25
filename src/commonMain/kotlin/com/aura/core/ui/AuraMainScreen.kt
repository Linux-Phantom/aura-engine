package com.aura.core.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AuraMainScreen(viewModel: AuraMainViewModel) {
    val state by viewModel.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header(state.isOnline, viewModel::toggleNetworkStatus)
            PredictorSection(
                inputText = state.inputText,
                suggestions = state.predictionSuggestions,
                isPreflightActive = state.isPreflightActive,
                onInputChanged = viewModel::onInputChanged,
                onSuggestionClick = viewModel::acceptSuggestion,
                onSend = viewModel::onSend,
                isSending = state.isSending
            )
            CompressorSection(
                prompt = state.optimizationPrompt,
                compressionResult = state.compressionResult,
                maskedText = state.maskedOptimizationText,
                isOptimizing = state.isOptimizing,
                onPromptChanged = viewModel::onOptimizationPromptChanged,
                onOptimize = viewModel::optimizePrompt
            )
            RouterStatus(isOnline = state.isOnline, isSending = state.isSending)
            ChatList(state.chatMessages)
        }
    }
}

@Composable
private fun Header(isOnline: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "AURA Engine", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = "On-device intelligence for every request", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = if (isOnline) "CLOUD READY" else "OFFLINE", color = if (isOnline) Color(0xFF147D64) else Color(0xFFB3261E), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = isOnline, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun PredictorSection(
    inputText: String,
    suggestions: List<String>,
    isPreflightActive: Boolean,
    onInputChanged: (String) -> Unit,
    onSuggestionClick: () -> Unit,
    onSend: () -> Unit,
    isSending: Boolean
) {
    ShowcaseCard(
        eyebrow = "01 / REAL-TIME PREDICTOR",
        title = "Predict the next move",
        description = "Trie-backed suggestions update locally as you type."
    ) {
        TextField(
            value = inputText,
            onValueChange = onInputChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Try: напиши код") },
            singleLine = true
        )
        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            suggestions.forEach { suggestion ->
                Text(
                    text = "›  $suggestion",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSuggestionClick)
                        .padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Text("Waiting for a recognizable prefix...", style = MaterialTheme.typography.bodySmall)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isPreflightActive) "Preflight ready" else "Local inference path",
                color = if (isPreflightActive) Color(0xFF147D64) else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            Button(onClick = onSend, enabled = inputText.isNotBlank() && !isSending) {
                Text(if (isSending) "Sending..." else "Send")
            }
        }
    }
}

@Composable
private fun CompressorSection(
    prompt: String,
    compressionResult: com.aura.core.compressor.CompressionResult?,
    maskedText: String,
    isOptimizing: Boolean,
    onPromptChanged: (String) -> Unit,
    onOptimize: () -> Unit
) {
    ShowcaseCard(
        eyebrow = "02 / COMPRESSOR + PRIVACY",
        title = "Optimize before the network",
        description = "Compress context and mask PII on-device before remote execution."
    ) {
        TextField(
            value = prompt,
            onValueChange = onPromptChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Long prompt") },
            minLines = 4
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(onClick = onOptimize, enabled = prompt.isNotBlank() && !isOptimizing) {
            Text(if (isOptimizing) "Optimizing..." else "Optimize Prompt")
        }
        compressionResult?.let { result ->
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("ORIGINAL", "${prompt.length} chars", Modifier.weight(1f))
                MetricCard("COMPRESSED", "${result.compressedText.length} chars", Modifier.weight(1f))
                MetricCard("SAVED", "${result.savedRatioPercent}%", Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("ANONYMIZED PAYLOAD", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(maskedText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RouterStatus(isOnline: Boolean, isSending: Boolean) {
    val local = !isOnline
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("03 / ROUTER STATUS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(if (local) "Local SLM" else "Cloud LLM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (local) "Offline-safe execution" else "Remote route available", style = MaterialTheme.typography.bodyMedium)
            }
            Text(if (isSending) "IN FLIGHT" else if (local) "LOCAL" else "CLOUD", color = if (local) Color(0xFFB3261E) else Color(0xFF147D64), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShowcaseCard(eyebrow: String, title: String, description: String, content: @Composable () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChatList(messages: List<Pair<String, String>>) {
    if (messages.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Conversation trace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        messages.forEach { message ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(message.first, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(message.second)
                }
            }
        }
    }
}
