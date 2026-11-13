package app.hikari.extension.all.hitomi

import java.io.InputStream

object NozomiDecoder {
    fun decode(input: InputStream): List<Long> {
        val ids = mutableListOf<Long>()
        val bytes = input.readBytes()
        var i = 0
        while (i + 3 < bytes.size) {
            val b0 = bytes[i].toLong() and 0xFFL
            val b1 = bytes[i + 1].toLong() and 0xFFL
            val b2 = bytes[i + 2].toLong() and 0xFFL
            val b3 = bytes[i + 3].toLong() and 0xFFL
            val id = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            ids.add(id)
            i += 4
        }
        return ids
    }
}
