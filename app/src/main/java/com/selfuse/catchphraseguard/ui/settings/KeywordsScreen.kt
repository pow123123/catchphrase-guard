@file:OptIn(ExperimentalLayoutApi::class)

package com.selfuse.catchphraseguard.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.selfuse.catchphraseguard.MonitorStateStore
import com.selfuse.catchphraseguard.MonitorUiState
import com.selfuse.catchphraseguard.ui.components.SectionHeader
import com.selfuse.catchphraseguard.ui.components.SubScreenScaffold
import com.selfuse.catchphraseguard.ui.onboarding.PRESET_WORDS
import com.selfuse.catchphraseguard.ui.onboarding.SUGGESTED_WORDS
import com.selfuse.catchphraseguard.ui.tabular

private val EXTRA_SUGGESTIONS = listOf("怎么说呢", "说白了")

@Composable
fun KeywordsScreen(
    state: MonitorUiState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    val existingWords = state.keywords.map { it.word }.toSet()
    val suggestions = (PRESET_WORDS + SUGGESTED_WORDS + EXTRA_SUGGESTIONS)
        .filter { it !in existingWords }

    fun submitInput() {
        val word = input.trim()
        if (word.isEmpty()) return
        val existing = state.keywords.firstOrNull { it.word == word }
        when {
            existing == null -> {
                if (MonitorStateStore.addKeyword(word)) {
                    input = ""
                }
            }
            !existing.enabled -> {
                MonitorStateStore.toggleKeyword(word, true)
                input = ""
            }
            else -> {
                Toast.makeText(context, "「$word」已经在列表里了", Toast.LENGTH_SHORT).show()
            }
        }
    }

    SubScreenScaffold(title = "盯防词", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(12) },
                    label = { Text("添加要戒的词") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitInput() }),
                    trailingIcon = {
                        IconButton(onClick = { submitInput() }, enabled = input.isNotBlank()) {
                            Icon(Icons.Filled.Add, contentDescription = "添加")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            if (suggestions.isNotEmpty()) {
                item { SectionHeader("常见词，点一下添加") }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        suggestions.forEach { word ->
                            AssistChip(
                                onClick = { MonitorStateStore.addKeyword(word) },
                                label = { Text(word) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item { SectionHeader("当前列表") }

            if (state.keywords.isEmpty()) {
                item {
                    Text(
                        text = "还没有盯防词，在上面输入或点选常见词添加。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(state.keywords, key = { it.word }) { item ->
                    ListItem(
                        headlineContent = { Text(item.word) },
                        supportingContent = {
                            Text(
                                text = "今天 ${item.todayCount} 次 · 累计 ${item.totalCount} 次",
                                style = MaterialTheme.typography.bodySmall.tabular()
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = item.enabled,
                                    onCheckedChange = { enabled ->
                                        MonitorStateStore.toggleKeyword(item.word, enabled)
                                    }
                                )
                                IconButton(onClick = { MonitorStateStore.removeKeyword(item.word) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
