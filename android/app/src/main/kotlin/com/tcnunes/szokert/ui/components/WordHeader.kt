package com.tcnunes.szokert.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcnunes.szokert.R
import com.tcnunes.szokert.core.Glossary
import com.tcnunes.szokert.core.Lemma
import com.tcnunes.szokert.core.commonness
import com.tcnunes.szokert.ui.theme.Szokert

/**
 * Headword, part of speech, commonness badge, IPA and the save star (WordHeader.svelte). On the
 * word page ([large]) the part of speech and badge open their Guide entries.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordHeader(
    lemma: Lemma,
    glossary: Glossary,
    saved: Boolean,
    onToggleSave: () -> Unit,
    large: Boolean = false,
    onGuide: ((String) -> Unit)? = null,
) {
    val c = Szokert.colors
    val pos = glossary.describePos(lemma.pos)
    val common = commonness(lemma.fr)
    Row(verticalAlignment = Alignment.Top) {
        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(lemma.w, color = c.text, fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = if (large) 34.sp else 26.sp)
            Text(
                pos.label,
                color = c.accent,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                modifier = if (onGuide != null) Modifier.clickable { onGuide("pos-${lemma.pos}") } else Modifier,
            )
            if (common != null) Pill(common.label, accent = true, onClick = onGuide?.let { { it("commonness") } })
            lemma.ipa?.let { Text(it, color = c.faint, fontSize = 14.sp) }
        }
        IconButton(onClick = onToggleSave, modifier = Modifier.size(40.dp)) {
            Icon(
                painterResource(if (saved) R.drawable.ic_star else R.drawable.ic_star_border),
                contentDescription = if (saved) "Remove ${lemma.w} from saved words" else "Save ${lemma.w}",
                tint = if (saved) c.star else c.faint,
            )
        }
    }
}
