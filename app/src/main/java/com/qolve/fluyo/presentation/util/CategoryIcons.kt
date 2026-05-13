package com.qolve.fluyo.presentation.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Theaters
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps the DB icon token (set by the seed trigger or future user-customized categories)
 * to a Material icon. Falls back to a generic tag icon for unknown tokens.
 */
fun iconForToken(token: String): ImageVector = when (token.lowercase()) {
    "utensils", "food", "restaurant" -> Icons.Outlined.Restaurant
    "fastfood" -> Icons.Outlined.Fastfood
    "bus" -> Icons.Outlined.DirectionsBus
    "train" -> Icons.Outlined.Train
    "gamepad", "games" -> Icons.Outlined.SportsEsports
    "music" -> Icons.Outlined.MusicNote
    "movie", "theater" -> Icons.Outlined.Theaters
    "coffee", "snacks" -> Icons.Outlined.LocalCafe
    "drink" -> Icons.Outlined.LocalDrink
    "heart", "health" -> Icons.Outlined.Favorite
    "hospital" -> Icons.Outlined.LocalHospital
    "book", "education" -> Icons.Outlined.Book
    "groceries", "supermarket" -> Icons.Outlined.LocalGroceryStore
    "shopping", "shop" -> Icons.Outlined.ShoppingBag
    "pet" -> Icons.Outlined.Pets
    "home", "rent" -> Icons.Outlined.Home
    "bolt", "utilities" -> Icons.Outlined.Bolt
    "money" -> Icons.Outlined.AttachMoney
    "tag" -> Icons.Outlined.Sell
    else -> Icons.Outlined.MoreHoriz
}

/** Parse a "#RRGGBB" hex string into a Compose [Color]. Falls back to gray on bad input. */
fun parseHexColor(hex: String): Color = runCatching {
    val trimmed = hex.removePrefix("#")
    val rgb = trimmed.toLong(16)
    Color(0xFF000000 or rgb)
}.getOrDefault(Color(0xFF78909C))
