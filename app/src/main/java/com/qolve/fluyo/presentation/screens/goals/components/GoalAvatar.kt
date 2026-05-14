package com.qolve.fluyo.presentation.screens.goals.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Decorative avatar for a savings goal. Until the domain layer carries a real `icon` field on
 * `Goal`, we infer one from the goal name using simple keyword matching. Color is derived
 * from a deterministic hash of the name so the same goal always looks the same.
 *
 * This is intentionally presentation-only — when the domain gets a real icon field, this
 * helper becomes a fallback for legacy rows.
 */
@Composable
fun GoalAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp,
) {
    val (emoji, color) = remember(name) { inferGoalVisual(name) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            style = TextStyle(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Normal),
        )
    }
}

internal fun inferGoalVisual(name: String): Pair<String, Color> {
    val lower = name.lowercase(Locale.ROOT)
    val emoji = when {
        contains(lower, "iphone", "celular", "phone", "smartphone", "móvil", "movil") -> "📱"
        contains(lower, "laptop", "computadora", "pc", "macbook", "compu") -> "💻"
        contains(lower, "audífono", "audifono", "audífonos", "audifonos", "headphone", "auricular") -> "🎧"
        contains(lower, "viaje", "vacaciones", "playa", "cusco", "europa", "machu", "trip") -> "✈️"
        contains(lower, "auto", "moto", "carro", "bicicleta", "bici") -> "🚗"
        contains(lower, "emergencia", "fondo", "ahorro") -> "🛟"
        contains(lower, "casa", "depa", "alquiler", "departamento") -> "🏠"
        contains(lower, "boda", "matrimonio") -> "💍"
        contains(lower, "curso", "universidad", "carrera", "estudios", "maestría", "maestria") -> "🎓"
        contains(lower, "regalo", "cumple", "navidad") -> "🎁"
        contains(lower, "concierto", "festival", "entrada") -> "🎤"
        contains(lower, "videojuego", "consola", "play", "xbox", "nintendo", "gaming") -> "🎮"
        contains(lower, "cámara", "camara", "foto") -> "📸"
        contains(lower, "ropa", "zapatos", "polo") -> "👕"
        else -> "🎯"
    }
    val color = palette[(stableHash(name) % palette.size + palette.size) % palette.size]
    return emoji to color
}

private fun contains(haystack: String, vararg needles: String): Boolean =
    needles.any { haystack.contains(it) }

/** djb2-style hash; stable across runs (unlike default String.hashCode in some Kotlin/JVM combos). */
private fun stableHash(s: String): Int {
    var h = 5381
    for (c in s) h = (h shl 5) + h + c.code
    return h
}

private val palette = listOf(
    Color(0xFF00897B), // Fluyo teal
    Color(0xFFFF7043), // Fluyo coral
    Color(0xFF7E57C2), // soft purple
    Color(0xFF42A5F5), // bright blue
    Color(0xFFEC407A), // pink
    Color(0xFF26A69A), // jade
    Color(0xFFFFB300), // amber
    Color(0xFF8D6E63), // warm brown
)
