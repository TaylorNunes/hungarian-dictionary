package com.tcnunes.szokert.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp
import com.tcnunes.szokert.ui.theme.Szokert

private val WORD = Regex("\\p{L}+(?:-\\p{L}+)*")

/** A Hungarian example with its translation; every Hungarian word can be tapped to look it up (Sentence.svelte). */
@Composable
fun Sentence(ex: List<String>, onWord: ((String) -> Unit)?, modifier: Modifier = Modifier) {
    val c = Szokert.colors
    val hu = ex.getOrElse(0) { "" }
    val text = buildAnnotatedString {
        var at = 0
        for (m in WORD.findAll(hu)) {
            append(hu.substring(at, m.range.first))
            if (onWord != null) {
                val link = LinkAnnotation.Clickable(m.value, TextLinkStyles(SpanStyle(color = c.text))) { onWord(m.value.lowercase()) }
                withLink(link) { append(m.value) }
            } else {
                append(m.value)
            }
            at = m.range.last + 1
        }
        append(hu.substring(at))
    }
    Column(modifier) {
        Text(text, color = c.text, fontSize = 15.sp)
        ex.getOrNull(1)?.let { Text(it, color = c.muted, fontSize = 14.sp) }
    }
}
