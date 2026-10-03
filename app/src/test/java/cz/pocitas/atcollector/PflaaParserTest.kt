package cz.pocitas.atcollector

import cz.pocitas.atcollector.source.PflaaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PflaaParserTest {
    @Test
    fun parsesTarget() {
        val t = PflaaParser.parse("s", "\$PFLAA,0,-1234,1234,220,2,DD8F12,180,,30,-1.4,1*4F")!!
        assertEquals("DD8F12", t.id)
        assertEquals(-1234, t.relativeNorthM)
        assertEquals(1234, t.relativeEastM)
        assertEquals(220, t.relativeVerticalM)
        assertEquals(30, t.groundSpeedMs)
        assertEquals(-1.4, t.climbRateMs!!, 0.0)
    }

    @Test
    fun ignoresOtherSentences() {
        assertNull(PflaaParser.parse("s", "\$GPGGA,1,2,3"))
    }
}
