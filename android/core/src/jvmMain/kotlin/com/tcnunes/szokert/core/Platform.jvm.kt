package com.tcnunes.szokert.core

import java.text.Normalizer

internal actual fun nfd(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
internal actual fun nfc(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC)
