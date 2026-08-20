package com.appia.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.appia.ai.trigger.AgentTriggerReceiver
import com.appia.ai.trigger.TriggerConfig
import com.appia.ai.trigger.TriggerStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    val saved by viewModel.triggerConfig.collectAsState()
    var enabled by remember(saved) { mutableStateOf(saved.enabled) }
    var instruction by remember(saved) { mutableStateOf(saved.instruction) }
    val timeState = rememberTimePickerState(
        initialHour = saved.hour,
        initialMinute = saved.minute,
        is24Hour = true
    )
    var showSaved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主动提醒") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "开启后，Agent 会在每天指定时间静默执行下面的指令，并通过通知告诉你结果。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("每日主动提醒", style = MaterialTheme.typography.titleSmall)
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }

                    if (enabled) {
                        TimePicker(
                            state = timeState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = instruction,
                            onValueChange = { instruction = it },
                            label = { Text("触发时执行的指令") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            minLines = 3
                        )
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.saveTriggerConfig(
                        TriggerConfig(
                            enabled = enabled,
                            hour = timeState.hour,
                            minute = timeState.minute,
                            instruction = instruction.trim().ifEmpty { TriggerConfig.DEFAULT_INSTRUCTION }
                        )
                    )
                    showSaved = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }

            if (showSaved) {
                Text(
                    if (enabled) "已保存，将在每天 ${"%02d:%02d".format(timeState.hour, timeState.minute)} 触发"
                    else "已保存，主动提醒已关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedButton(
                onClick = {
                    AgentTriggerReceiver.runNow(context, TriggerStore(context).load())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("立即测试触发一次")
            }

            Text(
                "注意：通知权限需要在 设置 → 工具权限管理 中开启，否则提醒发不出来。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
