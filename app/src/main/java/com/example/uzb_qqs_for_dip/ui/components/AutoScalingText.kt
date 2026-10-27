package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Текст, автоматически адаптирующий размер шрифта
 * в зависимости от разрешения экрана и ширины контейнера, чтобы не обрезаться и не переноситься.
 */
@Composable
fun AutoScalingText(
    text: String,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 7.5.sp,
    initialFontSize: TextUnit? = null,
    fontWeight: FontWeight = FontWeight.SemiBold,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Center
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val defaultInitialFontSize = when {
        screenWidth < 350 -> 9.5f
        screenWidth < 390 -> 10.5f
        screenWidth < 600 -> 11f
        else -> 12f
    }
    val startSize = initialFontSize?.value ?: defaultInitialFontSize
    var fontSizeValue by remember(text, screenWidth, startSize) { mutableFloatStateOf(startSize) }
    var readyToDraw by remember(text, screenWidth, startSize) { mutableStateOf(false) }

    Text(
        text = text,
        modifier = modifier.drawWithContent {
            if (readyToDraw) drawContent()
        },
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        color = color,
        fontWeight = fontWeight,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = fontSizeValue.sp,
            lineHeight = fontSizeValue.sp,
            textAlign = textAlign
        ),
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowWidth || textLayoutResult.didOverflowHeight) {
                if (fontSizeValue > minFontSize.value) {
                    fontSizeValue = (fontSizeValue - 0.5f).coerceAtLeast(minFontSize.value)
                } else {
                    readyToDraw = true
                }
            } else {
                readyToDraw = true
            }
        }
    )
}
