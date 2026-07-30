// Mirrors DESIGN.md §Typography: Literata carries headwords/display slots,
// Inter carries everything else. Both bundled variable fonts (OFL, notices in
// app/fonts-licenses/), instantiated per-weight via FontVariation (minSdk 26).
package io.github.jtrv.quickword.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.jtrv.quickword.R

// Variation-settings Font() is still marked experimental; it has shipped
// unchanged for years and minSdk 26 satisfies its API floor.
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(
    resId: Int,
    weight: FontWeight,
) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Literata =
    FontFamily(
        variable(R.font.literata, FontWeight.Normal),
        variable(R.font.literata, FontWeight.Medium),
        variable(R.font.literata, FontWeight.SemiBold),
    )

val Inter =
    FontFamily(
        variable(R.font.inter, FontWeight.Normal),
        variable(R.font.inter, FontWeight.Medium),
        variable(R.font.inter, FontWeight.SemiBold),
        variable(R.font.inter, FontWeight.Bold),
    )

// M3 default-instance base, then per-slot assignment. Headword slot is
// displaySmall: Literata 36/44 medium per DESIGN.md.
private val default = Typography()

val QuickWordTypography =
    Typography(
        displayLarge = default.displayLarge.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        displayMedium = default.displayMedium.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        displaySmall =
            TextStyle(
                fontFamily = Literata,
                fontWeight = FontWeight.Medium,
                fontSize = 36.sp,
                lineHeight = 44.sp,
            ),
        headlineLarge = default.headlineLarge.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        headlineMedium = default.headlineMedium.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        headlineSmall = default.headlineSmall.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        titleLarge = default.titleLarge.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        titleMedium = default.titleMedium.copy(fontFamily = Literata, fontWeight = FontWeight.Medium),
        titleSmall = default.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        bodyLarge = default.bodyLarge.copy(fontFamily = Inter),
        bodyMedium = default.bodyMedium.copy(fontFamily = Inter),
        bodySmall = default.bodySmall.copy(fontFamily = Inter),
        labelLarge = default.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        labelMedium = default.labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        labelSmall = default.labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
    )
