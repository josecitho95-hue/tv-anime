package com.jkanimetv.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

private val Default = FontFamily.SansSerif

val TvTypography = Typography(
    displayLarge   = TextStyle(fontFamily = Default, fontSize = 34.sp, fontWeight = FontWeight.Bold,     letterSpacing = (-0.5).sp),
    displayMedium  = TextStyle(fontFamily = Default, fontSize = 28.sp, fontWeight = FontWeight.Bold),
    displaySmall   = TextStyle(fontFamily = Default, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge  = TextStyle(fontFamily = Default, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontFamily = Default, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall  = TextStyle(fontFamily = Default, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleLarge     = TextStyle(fontFamily = Default, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium    = TextStyle(fontFamily = Default, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall     = TextStyle(fontFamily = Default, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge      = TextStyle(fontFamily = Default, fontSize = 16.sp, fontWeight = FontWeight.Normal,   lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = Default, fontSize = 14.sp, fontWeight = FontWeight.Normal,   lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = Default, fontSize = 12.sp, fontWeight = FontWeight.Normal,   lineHeight = 16.sp),
    labelLarge     = TextStyle(fontFamily = Default, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium    = TextStyle(fontFamily = Default, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall     = TextStyle(fontFamily = Default, fontSize = 11.sp, fontWeight = FontWeight.Medium)
)
