package com.vivekray898.payvoice.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii from DESIGN.md's rounded scale. */
val PvShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // rounded.xs
    small = RoundedCornerShape(6.dp), // rounded.sm
    medium = RoundedCornerShape(8.dp), // rounded.md
    large = RoundedCornerShape(12.dp), // rounded.lg
    extraLarge = RoundedCornerShape(16.dp), // rounded.xl
)
// Buttons use rounded.pill — apply via RoundedCornerShape(percent = 50).
