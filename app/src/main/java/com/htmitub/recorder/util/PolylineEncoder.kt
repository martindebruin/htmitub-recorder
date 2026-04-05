package com.htmitub.recorder.util

import kotlin.math.roundToInt

object PolylineEncoder {

    fun encode(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var prevLat = 0
        var prevLng = 0
        for ((lat, lng) in points) {
            val iLat = (lat * 1e5).roundToInt()
            val iLng = (lng * 1e5).roundToInt()
            encodeSignedInt(sb, iLat - prevLat)
            encodeSignedInt(sb, iLng - prevLng)
            prevLat = iLat
            prevLng = iLng
        }
        return sb.toString()
    }

    private fun encodeSignedInt(sb: StringBuilder, value: Int) {
        var v = value shl 1
        if (value < 0) v = v.inv()
        do {
            var chunk = v and 0x1f
            v = v ushr 5
            if (v > 0) chunk = chunk or 0x20
            sb.append((chunk + 63).toChar())
        } while (v > 0)
    }
}
