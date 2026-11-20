package com.selfuse.catchphraseguard.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.selfuse.catchphraseguard.VoiceCaptureResult
import com.selfuse.catchphraseguard.ui.components.VoiceCaptureButton

private data class EnrollQuestion(val question: String, val hint: String)

/** Three casual questions. Nothing to read aloud: the user just answers naturally. */
private val ENROLL_QUESTIONS = listOf(
    EnrollQuestion(
        question = "今天上一顿饭吃了什么？味道怎么样？",
        hint = "像发语音一样随口回答，说几句就行"
    ),
    EnrollQuestion(
        question = "听说你每天凌晨四点起床跑十公里，真的吗？",
        hint = "用你平时反驳别人的语气回答"
    ),
    EnrollQuestion(
        question = "把手机放到桌上，说说你周围现在吵不吵？",
        hint = "离手机远一点说，适应放在桌上或口袋里的情况"
    )
)

const val ENROLL_STEP_COUNT = 3

/**
 * Voice enrollment used both in onboarding and in Settings > 我的声音 > 重新录制.
 * Collects multi-scale embeddings from three answers and hands them to [onFinished].
 * [footer] renders under the button (e.g. a skip link).
 */
@Composable
fun VoiceEnrollment(
    onFinished: (embeddings: List<FloatArray>, stepCount: Int) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {}
) {
    var step by remember { mutableIntStateOf(0) }
    val collected = remember { mutableStateListOf<FloatArray>() }
    var recording by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf<String?>(null) }

    val current = ENROLL_QUESTIONS[step.coerceIn(0, ENROLL_QUESTIONS.lastIndex)]

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "第 ${step + 1} / $ENROLL_STEP_COUNT 个问题",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { step / ENROLL_STEP_COUNT.toFloat() },
            modifier = Modifier.fillMaxWidth(0.4f)
        )
        Spacer(Modifier.height(16.dp))
        AnimatedContent(targetState = current, label = "enrollQuestion") { q ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = q.question,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = q.hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        VoiceCaptureButton(
            multiScale = true,
            onRecordingChange = { recording = it; if (it) tip = null },
            onResult = { result ->
                when (result) {
                    is VoiceCaptureResult.Success -> {
                        collected.addAll(result.embeddings)
                        if (step < ENROLL_STEP_COUNT - 1) {
                            step++
                        } else {
                            onFinished(collected.toList(), ENROLL_STEP_COUNT)
                        }
                    }
                    is VoiceCaptureResult.TooQuiet -> tip = "刚才说得有点短，多说半句就好"
                    is VoiceCaptureResult.Error -> tip = result.message
                }
            }
        )
        val tipText = tip
        if (tipText != null) {
            Text(
                text = tipText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        if (collected.isNotEmpty() && !recording) {
            TextButton(onClick = { onFinished(collected.toList(), step) }) {
                Text("已录 $step 段，就用这些")
            }
        }
        footer()
    }
}
