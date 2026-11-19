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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfuse.catchphraseguard.ui.AuraMeshBackdrop
import com.selfuse.catchphraseguard.ui.CatchphraseOriginTheme
import com.selfuse.catchphraseguard.ui.LocalOriginPalette
import com.selfuse.catchphraseguard.ui.MicCanvasIcon
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
                CatchphraseAuraShell()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MonitorStateStore.rollOverDateIfNeeded()
    }
}

@Composable
private fun CatchphraseAuraShell() {
    val context = LocalContext.current
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Full-bleed ethereal pastel mesh aurora canvas
        AuraMeshBackdrop()

        Column(modifier = Modifier.fillMaxSize()) {
            AuraMinimalHeader(
                currentTab = currentTab,
                isRunning = uiState.isRunning
            )

            Box(modifier = Modifier.weight(1f)) {
                Crossfade(
                    targetState = currentTab,
                    animationSpec = tween(durationMillis = 240),
                    label = "auraTabCrossfade"
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

        // Floating Frosted-Glass Capsule Bottom Dock (matching Style A mockup)
        FloatingAuraGlassDock(
            currentTab = currentTab,
            onSelectTab = { currentTab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
        )
    }
}

@Composable
private fun AuraMinimalHeader(
    currentTab: Int,
    isRunning: Boolean
) {
    val palette = LocalOriginPalette.current
    val title = when (currentTab) {
        0 -> "Voice Radar"
        1 -> "Voiceprint Studio"
        else -> "Insights & Studio"
    }
    val subtitle = when (currentTab) {
        0 -> "口头禅流光声呐 · v3.1"
        1 -> "自然闲聊声纹与词库"
        else -> "统计看板与守护偏好"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 12.dp, bottom = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.width(7.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .shadow(
                        elevation = if (isRunning) 6.dp else 0.dp,
                        shape = CircleShape,
                        ambientColor = palette.accentCyan,
                        spotColor = palette.accentCyan
                    )
                    .clip(CircleShape)
                    .background(
                        if (isRunning) palette.accentCyan else palette.textMuted.copy(alpha = 0.45f)
                    )
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun FloatingAuraGlassDock(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalOriginPalette.current
    val dockShape = RoundedCornerShape(36.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = dockShape,
                ambientColor = palette.glassShadowColor,
                spotColor = palette.glassShadowColor
            )
            .clip(dockShape)
            .background(palette.navBarSurface)
            .border(BorderStroke(1.3.dp, palette.glassRimBrush), dockShape)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        DockBubbleItem(
            selected = currentTab == 0,
            label = "声呐",
            onClick = { onSelectTab(0) }
        ) { tint ->
            MicCanvasIcon(tint = tint, size = 21.dp)
        }

        DockBubbleItem(
            selected = currentTab == 1,
            label = "声纹",
            onClick = { onSelectTab(1) }
        ) { tint ->
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "声纹与词库",
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }

        DockBubbleItem(
            selected = currentTab == 2,
            label = "统计",
            onClick = { onSelectTab(2) }
        ) { tint ->
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "统计与设置",
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun DockBubbleItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    iconContent: @Composable (Color) -> Unit
) {
    val palette = LocalOriginPalette.current
    val interactionSource = remember { MutableInteractionSource() }

    val bgTarget = if (selected) {
        if (palette.isDark) Color(0xFF1E3A5F) else Color(0xFFE0F2FE)
    } else {
        Color.White.copy(alpha = if (palette.isDark) 0.05f else 0.45f)
    }
    val bubbleBg by animateColorAsState(
        targetValue = bgTarget,
        animationSpec = tween(durationMillis = 220),
        label = "dockBubbleBg"
    )
    val iconTint = if (selected) palette.accentBlue else palette.textSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(bubbleBg)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (selected) 0.85f else 0.35f),
                shape = RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (selected) 16.dp else 13.dp)
    ) {
        iconContent(iconTint)
        if (selected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accentBlue
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
