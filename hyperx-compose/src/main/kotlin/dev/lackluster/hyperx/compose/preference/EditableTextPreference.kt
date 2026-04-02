package dev.lackluster.hyperx.compose.preference

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.compose.base.IntegratedTextField
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max

@Composable
fun EditableTextPreference(
    title: String,
    summary: String? = null,
    textValue: MutableState<String>,
    textHint: String = "",
    enabled: Boolean = true,
    titleColor: BasicComponentColors = EditableTextPreferenceDefaults.titleColor(),
    summaryColor: BasicComponentColors = EditableTextPreferenceDefaults.summaryColor(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = null
            ) {
                if (enabled) {
                    coroutineScope.launch {
                        interactionSource.emit(FocusInteraction.Focus())
                    }
                }
            }
            .heightIn(min = 56.dp)
            .fillMaxWidth()
            .padding(EditableTextPreferenceDefaults.InsideMargin),
    ) {
        Layout(
            content = {
                Text(
                    text = title,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = titleColor.color(enabled)
                )
                summary?.let {
                    Text(
                        text = it,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = summaryColor.color(enabled)
                    )
                }
                IntegratedTextField(
                    text = textValue.value,
                    onTextChange = { textValue.value = it },
                    hint = textHint,
                    modifier = Modifier,
                    enabled = enabled,
                    insideMargin = PaddingValues(0.dp),
                    interactionSource = interactionSource
                )
            }
        ) { measurables, constraints ->
            val leftConstraints = constraints.copy(maxWidth = constraints.maxWidth / 2)
            val hasSummary = measurables.size > 2
            val titleText = measurables[0].measure(leftConstraints)
            val summaryText = (if (hasSummary) measurables[1] else null)?.measure(leftConstraints)
            val leftWidth = max(titleText.width, (summaryText?.width ?: 0))
            val leftHeight = titleText.height + (summaryText?.height ?: 0)
            val rightWidth = constraints.maxWidth - leftWidth - 16.dp.roundToPx()
            val rightConstraints = constraints.copy(maxWidth = rightWidth)
            val inputField = (if (hasSummary) measurables[2] else measurables[1]).measure(rightConstraints)
            val totalHeight = max(leftHeight, inputField.height)
            layout(constraints.maxWidth, totalHeight) {
                val titleY = (totalHeight - leftHeight) / 2
                titleText.placeRelative(0, titleY)
                summaryText?.placeRelative(0, titleY + titleText.height)
                inputField.placeRelative(constraints.maxWidth - inputField.width, (totalHeight - inputField.height) / 2)
            }
        }
    }
}

object EditableTextPreferenceDefaults {
    val InsideMargin = PaddingValues(16.dp)

    @Composable
    fun titleColor(
        color: Color = MiuixTheme.colorScheme.onSurface,
        disabledColor: Color = MiuixTheme.colorScheme.disabledOnSecondaryVariant
    ): BasicComponentColors {
        return BasicComponentColors(
            color = color,
            disabledColor = disabledColor
        )
    }

    @Composable
    fun summaryColor(
        color: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        disabledColor: Color = MiuixTheme.colorScheme.disabledOnSecondaryVariant
    ): BasicComponentColors {
        return BasicComponentColors(
            color = color,
            disabledColor = disabledColor
        )
    }
}

@Immutable
class BasicComponentColors(
    private val color: Color,
    private val disabledColor: Color
) {
    @Stable
    fun color(enabled: Boolean): Color = if (enabled) color else disabledColor
}