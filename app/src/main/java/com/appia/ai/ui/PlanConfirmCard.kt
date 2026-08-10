package com.appia.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.appia.ai.model.ExecutionStep
import com.appia.ai.model.TaskPlan

@Composable
fun PlanConfirmCard(
    plan: TaskPlan,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "操作计划",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "目标: ${plan.goal}",
                style = MaterialTheme.typography.bodyMedium
            )
            plan.steps.forEachIndexed { index, step ->
                StepRow(index + 1, step)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("确认执行")
                }
            }
        }
    }
}

@Composable
private fun StepRow(index: Int, step: ExecutionStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$index.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Text(
            step.description.ifEmpty { step.action.actionName },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
