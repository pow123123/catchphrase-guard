package com.selfuse.catchphraseguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.selfuse.catchphraseguard.ui.LocalOriginPalette
import com.selfuse.catchphraseguard.ui.MicCanvasIcon
import com.selfuse.catchphraseguard.ui.OriginPillBadge
import com.selfuse.catchphraseguard.ui.RadarConsoleTab
import com.selfuse.catchphraseguard.ui.StatsAndSettingsTab
import com.selfuse.catchphraseguard.ui.VoiceprintAndWordsTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonitorStateStore.ensureInitialized(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)
        enableEdgeToEdge()

        setContent {
            CatchphraseOriginTheme {
                CatchphraseMainShell()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MonitorStateStore.rollOverDateIfNeeded()
    }
}

@Composable
private fun CatchphraseMainShell() {
    val context = LocalContext.current
    val palette = LocalOriginPalette.current
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
            Toast.makeText(context, "需要麦克风权限才能监听口头禅", Toast.LENGTH_LONG).show()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.backgroundBrush)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                OriginTopHeader(uiState = uiState)
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.navBarSurface)
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(color = palette.cardBorder.copy(alpha = 0.7f))
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.height(68.dp)
                    ) {
                        val itemColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = palette.accentBlue,
                            selectedTextColor = palette.accentBlue,
                            indicatorColor = palette.accentBlue.copy(alpha = if (palette.isDark) 0.22f else 0.14f),
                            unselectedIconColor = palette.textMuted,
                            unselectedTextColor = palette.textMuted
                        )

                        NavigationBarItem(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            icon = {
                                MicCanvasIcon(
                                    tint = if (currentTab == 0) palette.accentBlue else palette.textMuted,
                                    size = 22.dp
                                )
                            },
                            label = {
                                Text("监听主控", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            },
                            colors = itemColors
                        )

                        NavigationBarItem(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "声纹与词库",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text("声纹与词库", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            },
                            colors = itemColors
                        )

                        NavigationBarItem(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "统计与设置",
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text("统计与设置", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            },
                            colors = itemColors
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(
                    targetState = currentTab,
                    animationSpec = tween(durationMillis = 220),
                    label = "tabCrossfade"
                ) { tab ->
                    when (tab) {
                        0 -> RadarConsoleTab(
                            uiState = uiState,
                            onToggleMonitoring = {
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
                            },
                            onJumpToVoiceprint = { currentTab = 1 }
                        )

                        1 -> VoiceprintAndWordsTab(uiState = uiState)

                        else -> StatsAndSettingsTab(
                            uiState = uiState,
                            onSendTestPush = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                ) {
                                    testPushPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    sendTestPush(context, uiState.settings)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OriginTopHeader(uiState: MonitorUiState) {
    val palette = LocalOriginPalette.current
    val vpReady = uiState.voiceprintProfile.isEnrolled && uiState.settings.voiceprintEnabled

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "口头禅猎手",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                OriginPillBadge(
                    backgroundColor = palette.accentBlue.copy(alpha = if (palette.isDark) 0.22f else 0.12f)
                ) {
                    Text(
                        text = "v2.0",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.accentBlue
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (vpReady) {
                    "全离线语音 + CAMP++ 声纹引擎 · 仅本人触发"
                } else {
                    "全离线本地识别 · 录音绝不上传云端"
                },
                fontSize = 12.sp,
                color = palette.textSecondary
            )
        }
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
    Toast.makeText(context, "已发送测试推送消息", Toast.LENGTH_SHORT).show()
}
