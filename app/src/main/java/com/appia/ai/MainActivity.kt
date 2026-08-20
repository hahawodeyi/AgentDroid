package com.appia.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.appia.ai.ui.ChatScreen
import com.appia.ai.ui.ChatViewModel
import com.appia.ai.ui.PermissionGuideScreen
import com.appia.ai.ui.SettingsScreen
import com.appia.ai.ui.ToolPermissionsScreen
import com.appia.ai.ui.TriggerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val viewModel: ChatViewModel = viewModel()

                    NavHost(
                        navController = navController,
                        startDestination = "chat"
                    ) {
                        composable("chat") {
                            ChatScreen(
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToPermission = { navController.navigate("permission") },
                                viewModel = viewModel
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToTools = { navController.navigate("tools") },
                                onNavigateToTrigger = { navController.navigate("trigger") },
                                viewModel = viewModel
                            )
                        }
                        composable("tools") {
                            ToolPermissionsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                viewModel = viewModel
                            )
                        }
                        composable("trigger") {
                            TriggerScreen(
                                onNavigateBack = { navController.popBackStack() },
                                viewModel = viewModel
                            )
                        }
                        composable("permission") {
                            PermissionGuideScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
