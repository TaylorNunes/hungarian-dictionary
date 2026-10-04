package com.tcnunes.szokert.core

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

internal actual fun nfd(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
internal actual fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)

// StrictMath is fdlibm, like V8's Math.log10, so scores computed from it tie exactly as on the web.
internal actual fun log10Exact(x: Double): Double = StrictMath.log10(x)

// On Android, java.text.Collator is backed by ICU, as V8's localeCompare is.
internal actual fun defaultHungarianOrder(): Comparator<String> {
    val collator = Collator.getInstance(Locale.forLanguageTag("hu"))
    return Comparator { a, b -> synchronized(collator) { collator.compare(a, b) } }
}
