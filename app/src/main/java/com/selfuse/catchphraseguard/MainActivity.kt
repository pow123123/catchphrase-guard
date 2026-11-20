@file:OptIn(ExperimentalMaterial3Api::class)

package com.selfuse.catchphraseguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfuse.catchphraseguard.ui.CatchphraseTheme
import com.selfuse.catchphraseguard.ui.HomeScreen
import com.selfuse.catchphraseguard.ui.SessionSummarySheet
import com.selfuse.catchphraseguard.ui.TrendScreen
import com.selfuse.catchphraseguard.ui.components.BarChartIcon
import com.selfuse.catchphraseguard.ui.onboarding.OnboardingScreen
import com.selfuse.catchphraseguard.ui.settings.KeywordsScreen
import com.selfuse.catchphraseguard.ui.settings.RecognitionCheckScreen
import com.selfuse.catchphraseguard.ui.settings.SettingsScreen
import com.selfuse.catchphraseguard.ui.settings.VoiceSettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MonitorStateStore.ensureInitialized(applicationContext)
        NotificationHelper.ensureChannels(applicationContext)
        enableEdgeToEdge()

        setContent {
            CatchphraseTheme {
                CatchphraseApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MonitorStateStore.rollOverDateIfNeeded()
    }
}

private enum class MainTab { HOME, TREND }

private enum class SubRoute { SETTINGS, KEYWORDS, VOICE, RECOGNITION_CHECK }

@Composable
private fun CatchphraseApp() {
    val state by MonitorStateStore.uiState.collectAsStateWithLifecycle()

    if (!state.initialized) {
        Surface(modifier = Modifier.fillMaxSize()) {}
        return
    }

    if (!state.onboardingCompleted) {
        OnboardingScreen(state = state)
        return
    }

    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var subRoute by rememberSaveable { mutableStateOf<SubRoute?>(null) }
    var returnToSettings by rememberSaveable { mutableStateOf(false) }

    val goBackFromSubRoute: () -> Unit = {
        if (returnToSettings) {
            subRoute = SubRoute.SETTINGS
            returnToSettings = false
        } else {
            subRoute = null
        }
    }

    BackHandler(enabled = subRoute != null) { goBackFromSubRoute() }

    when (subRoute) {
        null -> Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (currentTab) {
                                MainTab.HOME -> "口头禅"
                                MainTab.TREND -> "趋势"
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            returnToSettings = false
                            subRoute = SubRoute.SETTINGS
                        }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == MainTab.HOME,
                        onClick = { currentTab = MainTab.HOME },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("今天") }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.TREND,
                        onClick = { currentTab = MainTab.TREND },
                        icon = { Icon(BarChartIcon, contentDescription = null) },
                        label = { Text("趋势") }
                    )
                }
            }
        ) { padding ->
            when (currentTab) {
                MainTab.HOME -> HomeScreen(
                    state = state,
                    onEditWords = {
                        returnToSettings = false
                        subRoute = SubRoute.KEYWORDS
                    },
                    onOpenVoice = {
                        returnToSettings = false
                        subRoute = SubRoute.VOICE
                    },
                    modifier = Modifier.padding(padding)
                )

                MainTab.TREND -> TrendScreen(
                    state = state,
                    modifier = Modifier.padding(padding)
                )
            }
        }

        SubRoute.SETTINGS -> SettingsScreen(
            state = state,
            onBack = goBackFromSubRoute,
            onOpenKeywords = {
                returnToSettings = true
                subRoute = SubRoute.KEYWORDS
            },
            onOpenVoice = {
                returnToSettings = true
                subRoute = SubRoute.VOICE
            },
            onOpenRecognitionCheck = {
                returnToSettings = true
                subRoute = SubRoute.RECOGNITION_CHECK
            }
        )

        SubRoute.KEYWORDS -> KeywordsScreen(
            state = state,
            onBack = goBackFromSubRoute
        )

        SubRoute.VOICE -> VoiceSettingsScreen(
            state = state,
            onBack = goBackFromSubRoute
        )

        SubRoute.RECOGNITION_CHECK -> RecognitionCheckScreen(
            state = state,
            onBack = goBackFromSubRoute
        )
    }

    val pendingSummary = state.pendingSummary
    if (pendingSummary != null) {
        SessionSummarySheet(
            state = state,
            summary = pendingSummary,
            onDismiss = { MonitorStateStore.dismissSummary() }
        )
    }
}
