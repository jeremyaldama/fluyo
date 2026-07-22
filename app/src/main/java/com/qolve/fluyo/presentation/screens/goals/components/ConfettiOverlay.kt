package com.qolve.fluyo.presentation.screens.goals.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qolve.fluyo.presentation.theme.FluyoCoral
import com.qolve.fluyo.presentation.theme.FluyoCyan
import com.qolve.fluyo.presentation.theme.FluyoTeal
import com.qolve.fluyo.presentation.theme.FluyoTealLight
import com.qolve.fluyo.presentation.theme.WarningAmber
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(
    val angle: Float,
    val speed: Float,
    val color: Color,
    val rotation: Float,
    val size: Float,
)

@Composable
fun ConfettiOverlay(
    triggerKey: Any,
    durationMillis: Int = 2200,
    particleCount: Int = 60,
    onFinished: () -> Unit = {},
) {
    val palette = listOf(FluyoTeal, FluyoTealLight, FluyoCyan, FluyoCoral, WarningAmber)
    val random = remember(triggerKey) { Random(triggerKey.hashCode()) }
    val particles = remember(triggerKey) {
        List(particleCount) {
            Particle(
                angle = (random.nextFloat() * 360f),
                speed = 600f + random.nextFloat() * 700f,
                color = palette[random.nextInt(palette.size)],
                rotation = random.nextFloat() * 360f,
                size = 6f + random.nextFloat() * 8f,
            )
        }
    }

    val progress = remember(triggerKey) { Animatable(0f) }

    LaunchedEffect(triggerKey) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
        )
        onFinished()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height * 0.35f
        val gravity = 1400f
        val t = progress.value

        particles.forEach { p ->
            val rad = Math.toRadians(p.angle.toDouble())
            val vx = (p.speed * cos(rad)).toFloat()
            val vy = (p.speed * sin(rad)).toFloat() - 600f // initial upward push
            val x = cx + vx * t
            val y = cy + vy * t + 0.5f * gravity * t * t
            val alpha = (1f - t).coerceIn(0f, 1f)
            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = p.size,
                center = androidx.compose.ui.geometry.Offset(x, y),
            )
        }
    }
}

@Suppress("unused")
private val previewMarker = 0.dp
