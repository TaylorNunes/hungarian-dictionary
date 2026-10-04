package com.tcnunes.szokert.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcnunes.szokert.core.emphasisRuns
import com.tcnunes.szokert.ui.theme.Szokert

val Serif = FontFamily.Serif
val CardShape = RoundedCornerShape(12.dp)

/** A rounded card on the surface colour, like the website's result cards. */
@Composable
fun SzokertCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Szokert.colors
    Surface(
        color = c.surface,
        shape = CardShape,
        border = BorderStroke(1.dp, c.border),
        modifier = modifier.fillMaxWidth().clip(CardShape).let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), content = content)
    }
}

/** "MEANINGS", "EXAMPLES": small uppercase headings. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Szokert.colors.faint,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = modifier,
    )
}

/** A rounded label: "very common", or a grey usage label like "transitive". */
@Composable
fun Pill(text: String, accent: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = Szokert.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(if (accent) 999.dp else 4.dp))
            .background(if (accent) c.accentSoft else c.surface2)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = if (accent) 8.dp else 6.dp, vertical = 1.dp),
    ) {
        Text(
            text,
            color = if (accent) c.accentStrong else c.muted,
            fontSize = if (accent) 12.sp else 13.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Glossary text where *asterisks* mark Hungarian words, shown in italics. */
fun rich(text: String): AnnotatedString = buildAnnotatedString {
    for (run in emphasisRuns(text)) {
        if (run.em) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(run.text) } else append(run.text)
    }
}
