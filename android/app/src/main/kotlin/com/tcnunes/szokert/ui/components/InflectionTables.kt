package com.tcnunes.szokert.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcnunes.szokert.core.FormList
import com.tcnunes.szokert.core.Grid
import com.tcnunes.szokert.core.Section
import com.tcnunes.szokert.ui.theme.Szokert

private val SECONDARY = Regex("^(Potential|Personal infinitive|Other forms)")

/** Declension / conjugation grids and other forms (InflectionTable.svelte); long, less-used sections start folded. */
@Composable
fun InflectionTables(sections: List<Section>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (s in sections) {
            if (SECONDARY.containsMatchIn(s.title)) {
                var open by rememberSaveable(s.title) { mutableStateOf(false) }
                Text(
                    (if (open) "▾ " else "▸ ") + s.title,
                    color = Szokert.colors.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { open = !open }.padding(vertical = 4.dp),
                )
                AnimatedVisibility(open) { SectionBody(s) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.title, color = Szokert.colors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    SectionBody(s)
                }
            }
        }
    }
}

@Composable
private fun SectionBody(s: Section) {
    val c = Szokert.colors
    when (s) {
        is Grid -> Column(Modifier.horizontalScroll(rememberScrollState())) {
            if (s.cols.size > 1) {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("", modifier = Modifier.width(HEAD_WIDTH))
                    for (col in s.cols) Text(col, color = c.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(CELL_WIDTH))
                }
                HorizontalDivider(color = c.border)
            }
            for (row in s.rows) {
                Row(Modifier.padding(vertical = 5.dp)) {
                    Column(Modifier.width(HEAD_WIDTH)) {
                        Text(row.head, color = c.text, fontSize = 15.sp)
                        row.sub?.let { Text(it, color = c.faint, fontSize = 12.sp) }
                    }
                    for (cell in row.cells) {
                        Text(if (cell.isEmpty()) "—" else cell.joinToString(", "), color = c.text, fontSize = 15.sp, modifier = Modifier.width(CELL_WIDTH))
                    }
                }
                HorizontalDivider(color = c.border)
            }
        }
        is FormList -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (item in s.items) {
                Column {
                    Text(item.head, color = c.muted, fontSize = 13.sp)
                    Text(item.forms.joinToString(", "), color = c.text, fontSize = 15.sp)
                }
            }
        }
    }
}

private val HEAD_WIDTH = 120.dp
private val CELL_WIDTH = 150.dp
