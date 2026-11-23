package app.navi

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun decodesGooglePolylineReferenceExample() {
        // reference example from Google's polyline docs (precision 5)
        val pts = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        assertEquals(listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453), pts)
    }

    @Test
    fun formatsDurations() {
        assertEquals("10 Min.", formatDuration(600))
        assertEquals("1 Std.", formatDuration(3600))
        assertEquals("1 Std. 30 Min.", formatDuration(5400))
        assertEquals("1 h 30 min", formatDuration(5400, "h", "min"))
    }

    @Test
    fun hidesTinyDistances() {
        assertEquals(null, formatDistance(4.0))
        assertEquals("1,5 km", formatDistance(1500.0))
    }
}
