package com.kazaeza.thaimer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kazaeza.thaimer.audio.VoiceStatus
import com.kazaeza.thaimer.data.Settings
import com.kazaeza.thaimer.ui.components.SectionLabel
import com.kazaeza.thaimer.ui.theme.Thaimer
import com.kazaeza.thaimer.ui.theme.Type

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    voiceStatus: VoiceStatus,
    onChange: ((Settings) -> Settings) -> Unit,
    onFixVoice: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = Thaimer.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = p.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Настройки", style = Type.title, color = p.text)
            Spacer(Modifier.height(12.dp))
            Toggle("Голос тренера", "Называет упражнения и раунды по-русски", settings.voice) { v -> onChange { it.copy(voice = v) } }
            if (settings.voice && (voiceStatus == VoiceStatus.MISSING_RUSSIAN || voiceStatus == VoiceStatus.UNAVAILABLE)) {
                Text(
                    if (voiceStatus == VoiceStatus.UNAVAILABLE) "Синтез речи недоступен. Установи «Распознавание и синтез речи от Google» и нажми сюда."
                    else "Русский голос не установлен. Нажми, чтобы скачать его в настройках синтеза речи.",
                    style = Type.small, color = p.accent,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.accent.copy(alpha = 0.10f))
                        .clickable(onClick = onFixVoice)
                        .padding(12.dp),
                )
            }
            Toggle("Гонг и сигналы", "Гонг раунда, бипы 3-2-1, свисток взрыва", settings.sounds) { v -> onChange { it.copy(sounds = v) } }
            Toggle("Хлопок за 10 секунд", "Как в зале: предупреждение перед концом раунда", settings.tenSecondWarning) { v -> onChange { it.copy(tenSecondWarning = v) } }
            Toggle("Вибрация", "Короткий отклик на каждую смену отрезка", settings.vibration) { v -> onChange { it.copy(vibration = v) } }
            Spacer(Modifier.height(12.dp))
            SectionLabel("Подготовка перед стартом")
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10, 15, 20).forEach { s ->
                    val selected = settings.prepSeconds == s
                    Text(
                        if (s == 0) "нет" else "$s с",
                        style = Type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) p.onAccent else p.text,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) p.accent else p.surfaceAlt)
                            .clickable(role = Role.RadioButton) { onChange { it.copy(prepSeconds = s) } }
                            .padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Экран не гаснет во время тренировки. Если заблокировать телефон, таймер, гонг и голос продолжат работать, а управление будет в уведомлении.",
                style = Type.small, color = p.muted,
            )
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = Thaimer.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.body.copy(fontWeight = FontWeight.Medium), color = p.text)
            Text(subtitle, style = Type.small, color = p.muted)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, checkedThumbColor = p.onAccent),
        )
    }
}
