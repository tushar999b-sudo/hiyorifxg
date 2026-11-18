package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.permissions.RuntimePermissionCoordinator
import com.example.shizuku.ShizukuManager
import com.example.ui.HiyoriMainView
import com.example.ui.HiyoriViewModel
import com.example.ui.theme.HiyoriTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HiyoriViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        ShizukuManager.init(applicationContext)
    }

    override fun onResume() {
        super.onResume()
        ShizukuManager.refresh()
    }

    override fun onDestroy() {
        ShizukuManager.destroy()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { results ->
                RuntimePermissionCoordinator.onPermissionsResult(results)
            }

            DisposableEffect(permissionLauncher) {
                RuntimePermissionCoordinator.registerLauncher { perms ->
                    permissionLauncher.launch(perms)
                }
                onDispose {
                    RuntimePermissionCoordinator.unregisterLauncher()
                }
            }

            val settingsState by viewModel.settingsState.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = settingsState.darkTheme ?: systemDark

            HiyoriTheme(darkTheme = isDark) {
                HiyoriMainView(
                    viewModel = viewModel,
                    isDarkTheme = isDark
                )
            }
        }
    }
}
