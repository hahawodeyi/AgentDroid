package com.appia.ai.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.appia.ai.agent.SafetyDecision
import com.appia.ai.model.ExecutionStep

@Composable
fun SafetyConfirmDialog(
    step: ExecutionStep,
    decision: SafetyDecision,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    val title = when (decision) {
        SafetyDecision.BLOCK -> "操作被阻止"
        SafetyDecision.CONFIRM -> "需要确认操作"
        SafetyDecision.ALLOW -> ""
    }

    val message = buildString {
        append("操作: ${step.description}\n")
        append("目标: ${step.target ?: "无"}\n\n")
        when (decision) {
            SafetyDecision.CONFIRM -> append("此操作可能产生不可逆的影响，请确认是否执行。")
            SafetyDecision.BLOCK -> append("此操作被安全系统阻止，无法执行。")
            SafetyDecision.ALLOW -> {}
        }
    }

    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认执行")
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("取消")
            }
        }
    )
}
