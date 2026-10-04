package com.tcnunes.szokert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcnunes.szokert.core.Analysis
import com.tcnunes.szokert.ui.theme.Szokert

/**
 * How a searched form is built: "házat = ház + accusative" (Breakdown.svelte). [compact]: one line
 * of labels for the results list; otherwise every analysis with its hints, for the word page.
 */
@Composable
fun Breakdown(analyses: List<Analysis>, headword: String, searched: String, guessed: Boolean, compact: Boolean) {
    if (analyses.isEmpty()) return
    val c = Szokert.colors
    val shown = if (compact) analyses.take(1) else analyses
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = if (compact) 4.dp else 8.dp)) {
        for (a in shown) {
            val text = buildAnnotatedString {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(if (guessed) searched else a.form) }
                withStyle(SpanStyle(color = c.faint)) { append(if (compact) " → " else " = ") }
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.text)) { append(headword) }
                for (p in a.parts) {
                    withStyle(SpanStyle(color = c.faint)) { append(" + ") }
                    append(p.label)
                    if (!compact && p.hint != null) withStyle(SpanStyle(color = c.muted, fontSize = 13.sp)) { append(" (${p.hint})") }
                }
                if (compact && analyses.size > 1) withStyle(SpanStyle(color = c.faint, fontSize = 13.sp)) { append("  +${analyses.size - 1} more") }
                if (guessed) withStyle(SpanStyle(color = c.secondary, fontSize = 13.sp)) { append("  · guess") }
            }
            Text(
                text,
                color = if (compact) c.muted else c.text,
                fontSize = 15.sp,
                modifier = if (compact) Modifier else Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.accentSoft).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
