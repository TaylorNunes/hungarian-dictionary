package com.tcnunes.szokert.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcnunes.szokert.core.Sense
import com.tcnunes.szokert.core.labelName
import com.tcnunes.szokert.ui.theme.Szokert

/**
 * The numbered meanings (Senses.svelte). [highlight]: the meaning an English search matched, marked
 * in place. [examples]: show each meaning's Wiktionary examples and make labels open the Guide.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Senses(
    senses: List<Sense>,
    highlight: Int? = null,
    examples: Boolean = false,
    onLabel: ((String) -> Unit)? = null,
    onWord: ((String) -> Unit)? = null,
) {
    val c = Szokert.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        senses.forEachIndexed { i, s ->
            val match = i == highlight
            Row(
                Modifier.clip(RoundedCornerShape(6.dp)).let { if (match) it.background(c.accentSoft) else it }
                    .padding(horizontal = if (match) 6.dp else 0.dp, vertical = if (match) 2.dp else 0.dp),
            ) {
                Text("${i + 1}.", color = c.faint, fontSize = 15.sp, modifier = Modifier.width(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (label in s.t.orEmpty()) {
                            Pill(labelName(label), onClick = if (examples && onLabel != null) ({ onLabel(label) }) else null)
                        }
                        s.p?.let { Text("$it ›", color = c.muted, fontSize = 15.sp) }
                        Text(s.g, color = if (match) c.accentStrong else c.text, fontSize = 15.sp)
                    }
                    if (examples) {
                        for (ex in s.ex.orEmpty()) {
                            Sentence(ex, onWord = onWord, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
