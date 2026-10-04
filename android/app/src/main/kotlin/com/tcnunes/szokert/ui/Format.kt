package com.tcnunes.szokert.ui

import com.tcnunes.szokert.data.Progress

/** 23356100 → "23 MB". */
fun megabytes(bytes: Long): String = "${(bytes + 500_000) / 1_000_000} MB"

/** "2026-10-04T07:25:09Z" → "2026-10-04". */
fun day(timestamp: String): String = timestamp.substringBefore('T')

fun describe(p: Progress?): String = when (p) {
    is Progress.Downloading -> "Downloading… ${megabytes(p.done)} of ${megabytes(p.total)}"
    Progress.Verifying -> "Checking the download…"
    is Progress.Unpacking -> "Unpacking…"
    null -> "Waiting for a connection…"
}
