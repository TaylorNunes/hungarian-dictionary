package com.tcnunes.szokert

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcnunes.szokert.data.DataManager
import com.tcnunes.szokert.ui.DataDialog
import com.tcnunes.szokert.ui.DownloadScreen
import com.tcnunes.szokert.ui.SearchScreen
import com.tcnunes.szokert.ui.theme.Szokert
import com.tcnunes.szokert.ui.theme.SzokertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark for now (a theme setting comes later), so the system bars get light icons.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        val data = (application as SzokertApp).data
        setContent {
            SzokertTheme {
                Box(Modifier.fillMaxSize().background(Szokert.colors.bg)) {
                    App(data)
                }
            }
        }
    }
}

@Composable
private fun App(data: DataManager) {
    val state by data.state.collectAsStateWithLifecycle()
    var showData by rememberSaveable { mutableStateOf(false) }
    // The download notification needs permission on Android 13+; the download runs either way.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { data.download() }
    val startDownload = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        else data.download()
    }

    LaunchedEffect(Unit) {
        runCatching { data.checkForUpdate() }
    }

    val installed = state.installed
    when {
        state.loading -> {}
        installed == null -> DownloadScreen(state.task, onDownload = startDownload)
        else -> SearchScreen(installed.source, state, onUpdate = startDownload, onShowData = { showData = true })
    }
    if (showData) DataDialog(state, data, onDismiss = { showData = false })
}
