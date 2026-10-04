package com.tcnunes.szokert.db

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

internal actual fun inflate(data: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(data)
    val out = ByteArrayOutputStream(data.size * 4)
    val buffer = ByteArray(16 * 1024)
    try {
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) error("truncated zlib data")
            out.write(buffer, 0, n)
        }
    } finally {
        inflater.end()
    }
    return out.toByteArray()
}
