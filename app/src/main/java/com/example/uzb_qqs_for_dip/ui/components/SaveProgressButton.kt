package com.example.uzb_qqs_for_dip.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Кнопка с заливкой прогресса слева направо. Используется при длительных
 * операциях сохранения и обновления данных.
 */
@Composable
fun SaveProgressButton(
    modifier: Modifier = Modifier,
    text: String,
    progressLabel: String,
    icon: ImageVector? = null,
    isSaving: Boolean,
    progress: Float,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val trackColor = MaterialTheme.colorScheme.secondaryContainer
    val fillColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "saveProgress"
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(trackColor)
            .alpha(if (enabled || isSaving) 1f else 0.5f)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(fillColor)
                    .align(Alignment.CenterStart)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (!isSaving && icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor)
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text = if (isSaving) progressLabel else text,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
