package com.qolve.fluyo.domain.model

/** Catalog of the 5 levels mapped to point thresholds (per CLAUDE.md). */
data class UserLevel(
    val number: Int,
    val key: String,
    val minPoints: Int,
    val nextThreshold: Int?,
) {
    fun progressTo(totalPoints: Int): Float {
        val ceiling = nextThreshold ?: return 1f
        val span = (ceiling - minPoints).coerceAtLeast(1)
        return ((totalPoints - minPoints).toFloat() / span).coerceIn(0f, 1f)
    }
}

object UserLevelCatalog {
    val levels: List<UserLevel> = listOf(
        UserLevel(1, "novato", 0, 20),
        UserLevel(2, "aprendiz", 20, 50),
        UserLevel(3, "organizado", 50, 100),
        UserLevel(4, "experto", 100, 200),
        UserLevel(5, "maestro", 200, null),
    )

    fun levelFor(totalPoints: Int): UserLevel =
        levels.last { totalPoints >= it.minPoints }
}
