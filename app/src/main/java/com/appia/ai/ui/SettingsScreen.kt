package com.appia.ai.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.appia.ai.agent.AgentMode
import com.appia.ai.llm.ModelConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTools: () -> Unit,
    onNavigateToTrigger: () -> Unit,
    onNavigateToAppia: () -> Unit,
    viewModel: ChatViewModel
) {
    val configs by viewModel.configs.collectAsState()
    val activeId by viewModel.activeConfigId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型设置") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.refreshActiveConfig()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val presets = viewModel.presets()
                val newConfig = presets.first().copy(
                    providerId = "config_${System.currentTimeMillis()}"
                )
                viewModel.saveConfigs(configs + newConfig)
            }) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        }
    ) { padding ->
        if (configs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("还没有配置任何模型", style = MaterialTheme.typography.titleMedium)
                Text(
                    "点击右下角 + 添加配置，或从预设模板快速创建。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.material3.TextButton(onClick = onNavigateToTools) {
                    Text("工具权限管理 →")
                }
                androidx.compose.material3.TextButton(onClick = onNavigateToTrigger) {
                    Text("主动提醒 →")
                }
                androidx.compose.material3.TextButton(onClick = onNavigateToAppia) {
                    Text("Appia 连接 →")
                }
                AgentModeCard(viewModel)
                PresetButtons(
                    onAddPreset = { preset ->
                        val newConfig = preset.copy(providerId = "config_${System.currentTimeMillis()}")
                        viewModel.saveConfigs(configs + newConfig)
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                item {
                    AgentModeCard(viewModel)
                }
                item {
                    androidx.compose.material3.TextButton(onClick = onNavigateToTools) {
                        Text("工具权限管理 →")
                    }
                }
                item {
                    androidx.compose.material3.TextButton(onClick = onNavigateToTrigger) {
                        Text("主动提醒 →")
                    }
                }
                item {
                    androidx.compose.material3.TextButton(onClick = onNavigateToAppia) {
                        Text("Appia 连接 →")
                    }
                }
                items(configs, key = { it.providerId }) { config ->
                    ConfigCard(
                        config = config,
                        isActive = config.providerId == activeId,
                        onUpdate = { updated ->
                            val newList = configs.map { if (it.providerId == updated.providerId) updated else it }
                            viewModel.saveConfigs(newList)
                        },
                        onDelete = {
                            val newList = configs.filter { it.providerId != config.providerId }
                            viewModel.saveConfigs(newList)
                        },
                        onActivate = {
                            viewModel.saveActiveConfig(config.providerId)
                        }
                    )
                }
                item {
                    PresetButtons(
                        onAddPreset = { preset ->
                            val newConfig = preset.copy(providerId = "config_${System.currentTimeMillis()}")
                            viewModel.saveConfigs(configs + newConfig)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentModeCard(viewModel: ChatViewModel) {
    val current by viewModel.agentMode.collectAsState()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Agent 工具调用模式", style = MaterialTheme.typography.titleSmall)
            AgentMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setAgentMode(mode) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = current == mode,
                        onClick = { viewModel.setAgentMode(mode) }
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(mode.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            mode.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetButtons(onAddPreset: (ModelConfig) -> Unit) {
    val presets = ModelConfig::class // placeholder, using viewModel.presets would require scope
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("预设模板", style = MaterialTheme.typography.titleSmall)
        com.appia.ai.llm.ModelRegistry.run {
            presets().forEach { preset ->
                androidx.compose.material3.TextButton(onClick = { onAddPreset(preset) }) {
                    Text("+ ${preset.displayName} (${preset.model})")
                }
            }
        }
    }
}

@Composable
private fun ConfigCard(
    config: ModelConfig,
    isActive: Boolean,
    onUpdate: (ModelConfig) -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit
) {
    var displayName by remember(config.providerId) { mutableStateOf(config.displayName) }
    var baseUrl by remember(config.providerId) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config.providerId) { mutableStateOf(config.apiKey) }
    var model by remember(config.providerId) { mutableStateOf(config.model) }
    var showApiKey by remember(config.providerId) { mutableStateOf(false) }

    fun commit() {
        val updated = config.copy(
            displayName = displayName,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model
        )
        onUpdate(updated)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    if (isActive) "★ ${config.displayName}" else config.displayName,
                    style = MaterialTheme.typography.titleSmall
                )
                Row {
                    IconButton(onClick = onActivate) {
                        Icon(Icons.Default.Check, contentDescription = "设为激活")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                }
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; commit() },
                label = { Text("显示名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; commit() },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; commit() },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Text(if (showApiKey) "隐藏" else "显示")
                    }
                }
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; commit() },
                label = { Text("模型名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
