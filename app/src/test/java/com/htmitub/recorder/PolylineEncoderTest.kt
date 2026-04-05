package com.htmitub.recorder

import com.htmitub.recorder.util.PolylineEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineEncoderTest {

    @Test
    fun `encodes known Google documentation example`() {
        // Source: https://developers.google.com/maps/documentation/utilities/polylinealgorithm
        val points = listOf(
            Pair(38.5, -120.2),
            Pair(40.7, -120.95),
            Pair(43.252, -126.453),
        )
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", PolylineEncoder.encode(points))
    }

    @Test
    fun `encodes empty list to empty string`() {
        assertEquals("", PolylineEncoder.encode(emptyList()))
    }

    @Test
    fun `encodes single point`() {
        val result = PolylineEncoder.encode(listOf(Pair(0.0, 0.0)))
        assertEquals("??", result)
    }
}
