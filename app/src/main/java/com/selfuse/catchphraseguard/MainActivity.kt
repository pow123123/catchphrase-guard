package com.selfuse.catchphraseguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfuse.catchphraseguard.ui.CatchphraseOriginTheme
import com.selfuse.catchphraseguard.ui.ChartBarsTabIcon
import com.selfuse.catchphraseguard.ui.FaderSlidersTabIcon
import com.selfuse.catchphraseguard.ui.LocalVinylPalette
import com.selfuse.catchphraseguard.ui.MiniVinylPlayerBar
import com.selfuse.catchphraseguard.ui.RadarConsoleTab
import com.selfuse.catchphraseguard.ui.StatsAndSettingsTab
import com.selfuse.catchphraseguard.ui.VinylAmbientBackdrop
import com.selfuse.catchphraseguard.ui.VinylDiscTabIcon
import com.selfuse.catchphraseguard.ui.VoiceprintAndWordsTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonitorStateStore.ensureInitialized(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            CatchphraseOriginTheme {
                CatchphraseNetEaseVinylShell()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MonitorStateStore.rollOverDateIfNeeded()
    }
}

@Composable
private fun CatchphraseNetEaseVinylShell() {
    val context = LocalContext.current
    val palette = LocalVinylPalette.current
    val uiState by MonitorStateStore.uiState.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    val startServicePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            hasPermission(context, Manifest.permission.RECORD_AUDIO)

        if (micGranted) {
            startMonitorService(context)
        } else {
            Toast.makeText(context, "需要麦克风权限才能开启黑胶监听", Toast.LENGTH_LONG).show()
        }
    }

    val testPushPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sendTestPush(context, uiState.settings)
        } else {
            Toast.makeText(context, "请开启通知权限以接收口头禅提醒", Toast.LENGTH_LONG).show()
        }
    }

    val handleToggleMonitoring: () -> Unit = {
        if (uiState.isRunning) {
            stopMonitorService(context)
        } else {
            val missing = mutableListOf<String>()
            if (!hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                missing.add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            ) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (missing.isEmpty()) {
                startMonitorService(context)
            } else {
                startServicePermissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    val handleTestPush: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            testPushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            sendTestPush(context, uiState.settings)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-screen NetEase Dark Crimson-Obsidian Vinyl Backdrop
        VinylAmbientBackdrop(isRunning = uiState.isRunning)

        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                Crossfade(
                    targetState = currentTab,
                    animationSpec = tween(durationMillis = 220),
                    label = "vinylPageCrossfade"
                ) { tab ->
                    when (tab) {
                        0 -> RadarConsoleTab(
                            uiState = uiState,
                            onToggleMonitoring = handleToggleMonitoring,
                            onJumpToVoiceprint = { currentTab = 1 },
                            onSendTestPush = handleTestPush
                        )

                        1 -> VoiceprintAndWordsTab(uiState = uiState)

                        else -> StatsAndSettingsTab(
                            uiState = uiState,
                            onSendTestPush = handleTestPush
                        )
                    }
                }
            }

            // Persistent NetEase Mini Vinyl Player Bar on Tabs 1 & 2
            AnimatedVisibility(
                visible = currentTab != 0,
                enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 2 }
            ) {
                MiniVinylPlayerBar(
                    uiState = uiState,
                    onOpenTurntable = { currentTab = 0 },
                    onTogglePlay = handleToggleMonitoring,
                    modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
                )
            }

            // NetEase Dark Vinyl 3-Tab Bottom Navigation Bar
            NetEaseBottomTabBar(
                currentTab = currentTab,
                onSelectTab = { currentTab = it }
            )
        }
    }
}

@Composable
private fun NetEaseBottomTabBar(
    currentTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val palette = LocalVinylPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.bottomBarSurface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = Color(0xFF22242E))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            NetEaseTabItem(
                selected = currentTab == 0,
                label = "监听",
                onClick = { onSelectTab(0) }
            ) { tint ->
                VinylDiscTabIcon(tint = tint, selected = currentTab == 0, size = 23.dp)
            }

            NetEaseTabItem(
                selected = currentTab == 1,
                label = "声纹榜",
                onClick = { onSelectTab(1) }
            ) { tint ->
                ChartBarsTabIcon(tint = tint, size = 23.dp)
            }

            NetEaseTabItem(
                selected = currentTab == 2,
                label = "调音台",
                onClick = { onSelectTab(2) }
            ) { tint ->
                FaderSlidersTabIcon(tint = tint, size = 23.dp)
            }
        }
    }
}

@Composable
private fun NetEaseTabItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    iconContent: @Composable (Color) -> Unit
) {
    val palette = LocalVinylPalette.current
    val tint = if (selected) palette.neteaseRed else palette.textMuted
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        iconContent(tint)
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint
        )
    }
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun startMonitorService(context: Context) {
    val intent = Intent(context, MonitorForegroundService::class.java).apply {
        action = MonitorForegroundService.ACTION_START
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun stopMonitorService(context: Context) {
    val intent = Intent(context, MonitorForegroundService::class.java).apply {
        action = MonitorForegroundService.ACTION_STOP
    }
    context.startService(intent)
}

private fun sendTestPush(context: Context, settings: MonitorSettings) {
    NotificationHelper.triggerCatchphraseAlert(
        context = context,
        word = "没有 / 不是（测试）",
        todayCount = 1,
        snippet = "看到顶部悬浮横幅，说明通知与声纹提醒通道一切正常！",
        pushEnabled = true,
        vibrateEnabled = settings.vibrateEnabled,
        similarity = 0.82f
    )
    Toast.makeText(context, "已发送测试强提醒推送", Toast.LENGTH_SHORT).show()
}
