package com.selfuse.catchphraseguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val AppColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF0F172A),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onSurface = Color(0xFF0F172A)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonitorStateStore.ensureInitialized(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(colorScheme = AppColors) {
                CatchphraseGuardScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MonitorStateStore.rollOverDateIfNeeded()
    }
}

@Composable
private fun CatchphraseGuardScreen() {
    val context = LocalContext.current
    val uiState by MonitorStateStore.uiState.collectAsStateWithLifecycle()
    var newKeywordText by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
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
            Toast.makeText(context, "请允许通知权限以接收推送提醒", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background( Color.White )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "口头禅猎手",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "100% 手机本地离线识别 · 零上传 · 实时推送提醒",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeroControlCard(
                    uiState = uiState,
                    onToggleService = {
                        if (uiState.isRunning) {
                            stopMonitorService(context)
                        } else {
                            val needed = mutableListOf<String>()
                            if (!hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                needed.add(Manifest.permission.RECORD_AUDIO)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            ) {
                                needed.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            if (needed.isEmpty()) {
                                startMonitorService(context)
                            } else {
                                permissionLauncher.launch(needed.toTypedArray())
                            }
                        }
                    },
                    onTestPush = {
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

            item {
                KeywordManagerCard(
                    keywords = uiState.keywords,
                    newKeywordText = newKeywordText,
                    onNewKeywordChange = { newKeywordText = it },
                    onAddKeyword = {
                        val added = MonitorStateStore.addKeyword(newKeywordText)
                        if (added) {
                            newKeywordText = ""
                        } else {
                            Toast.makeText(context, "请输入不重复的口头禅", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onToggleKeyword = { word, enabled ->
                        MonitorStateStore.toggleKeyword(word, enabled)
                    },
                    onDeleteKeyword = { word ->
                        MonitorStateStore.removeKeyword(word)
                    },
                    onResetToday = {
                        MonitorStateStore.resetTodayCounts()
                        Toast.makeText(context, "今日计数与记录已清零", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                SettingsCard(
                    settings = uiState.settings,
                    onUpdate = { transform -> MonitorStateStore.updateSettings(transform) }
                )
            }

            item {
                VivoOriginOsGuideCard(context = context)
            }

            item {
                RecentHitsCard(recentHits = uiState.recentHits)
            }
        }
    }
}

@Composable
private fun HeroControlCard(
    uiState: MonitorUiState,
    onToggleService: () -> Unit,
    onTestPush: () -> Unit
) {
    val todayTotal = uiState.keywords.sumOf { it.todayCount }
    val statusColor = if (uiState.isRunning) Color(0xFF16A34A) else Color(0xFF94A3B8)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.statusMessage,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }
                Text(
                    text = "今日累计 $todayTotal 次",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0284C7)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onToggleService,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isRunning) Color(0xFFDC2626) else Color(0xFF0284C7)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = if (uiState.isRunning) "停止后台监听" else "开始后台监听",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.isRunning) {
                Spacer(modifier = Modifier.height(12.dp))
                val levelProgress = (uiState.micLevel * 8f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { levelProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF0EA5E9),
                    trackColor = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (uiState.livePreviewText.isNotBlank()) {
                        "实时听写预览：${uiState.livePreviewText}"
                    } else {
                        "实时听写预览：（对着手机说一句「没有」或「不是」试试）"
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF475569)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onTestPush,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "发送一条测试推送消息", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun KeywordManagerCard(
    keywords: List<KeywordItem>,
    newKeywordText: String,
    onNewKeywordChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onToggleKeyword: (String, Boolean) -> Unit,
    onDeleteKeyword: (String) -> Unit,
    onResetToday: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "盯防口头禅列表",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                TextButton(onClick = onResetToday) {
                    Text(text = "清零今日计数", fontSize = 13.sp)
                }
            }

            if (keywords.isEmpty()) {
                Text(
                    text = "还没有口头禅，在下方输入添加即可",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                keywords.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "「${item.word}」",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.enabled) Color(0xFF0F172A) else Color(0xFF94A3B8)
                            )
                            Text(
                                text = "今日 ${item.todayCount} 次 · 累计 ${item.totalCount} 次",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = item.enabled,
                            onCheckedChange = { checked -> onToggleKeyword(item.word, checked) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = { onDeleteKeyword(item.word) }) {
                            Text(text = "删除", color = Color(0xFFEF4444), fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newKeywordText,
                    onValueChange = onNewKeywordChange,
                    placeholder = { Text("输入新口头禅，如：那个、其实", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAddKeyword,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("添加")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    settings: MonitorSettings,
    onUpdate: ((MonitorSettings) -> MonitorSettings) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "提醒与收音设置",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            SettingSwitchRow(
                title = "手机消息推送提醒",
                subtitle = "说到口头禅时立即弹出系统横幅消息",
                checked = settings.pushAlertEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(pushAlertEnabled = v) } }
            )

            HorizontalDivider(color = Color(0xFFF1F5F9))

            SettingSwitchRow(
                title = "同时触发三连短震",
                subtitle = "手机放口袋静音时也能靠体感分辨",
                checked = settings.vibrateEnabled,
                onCheckedChange = { v -> onUpdate { it.copy(vibrateEnabled = v) } }
            )

            HorizontalDivider(color = Color(0xFFF1F5F9))

            SettingSwitchRow(
                title = "口袋/桌面远场增益",
                subtitle = "手机揣兜里或离嘴较远时放大麦克风灵敏度",
                checked = settings.micBoost,
                onCheckedChange = { v -> onUpdate { it.copy(micBoost = v) } }
            )

            HorizontalDivider(color = Color(0xFFF1F5F9))

            SettingSwitchRow(
                title = "过滤远处小声背景音",
                subtitle = "忽略音量过低的杂音，减少旁人说话误报",
                checked = settings.nearVoiceFilter,
                onCheckedChange = { v -> onUpdate { it.copy(nearVoiceFilter = v) } }
            )

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Column {
                Text(
                    text = "同一口头禅防刷屏冷却：${settings.cooldownSeconds} 秒",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "避免连说一句「没有没有」在半秒内重复弹两条消息",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 10).forEach { sec ->
                        val selected = settings.cooldownSeconds == sec
                        if (selected) {
                            Button(
                                onClick = {},
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("${sec}秒", fontSize = 13.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onUpdate { it.copy(cooldownSeconds = sec) } },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("${sec}秒", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun VivoOriginOsGuideCard(context: Context) {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var ignoringBattery by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "vivo OriginOS 锁屏稳定推送设置（只需设一次）",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0C4A6E)
            )
            Text(
                text = "1. 点击下方「通知设置」，确认开启【悬浮通知】和【锁屏通知】。\n" +
                    "2. 点击「申请后台免杀」，并在 vivo【设置 → 电池 → 后台耗电管理】中将本应用改为【允许后台高耗电】，锁屏放兜里几小时也不会断。",
                fontSize = 12.sp,
                color = Color(0xFF334155),
                lineHeight = 18.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { openAppNotificationSettings(context) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("通知设置", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = {
                        requestIgnoreBatteryOptimizations(context)
                        ignoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (ignoringBattery) "已免除电池限制" else "申请后台免杀",
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentHitsCard(recentHits: List<HitRecord>) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "最近抓包记录（保留最近 30 条）",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (recentHits.isEmpty()) {
                Text(
                    text = "暂无命中记录，开始监听后说到口头禅会记录在这里",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            } else {
                recentHits.forEachIndexed { index, hit ->
                    if (index > 0) {
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                    }
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "命中【${hit.word}】（今日第 ${hit.todayCountAfterHit} 次）",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFDC2626)
                            )
                            Text(
                                text = hit.time,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        if (hit.snippet.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "语境：「${hit.snippet}」",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                }
            }
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
        snippet = "这是一条测试推送，看到顶部横幅说明通知权限已正常！",
        pushEnabled = true,
        vibrateEnabled = settings.vibrateEnabled
    )
    Toast.makeText(context, "已发送测试推送，请查看顶部通知栏", Toast.LENGTH_SHORT).show()
}

private fun openAppNotificationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppDetails(context)
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            @Suppress("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } else {
            openAppDetails(context)
        }
    } catch (_: Exception) {
        openAppDetails(context)
    }
}

private fun openAppDetails(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
