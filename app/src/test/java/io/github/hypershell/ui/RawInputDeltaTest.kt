package io.github.hypershell.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RawInputDeltaTest {
    @Test
    fun repeatedSpacesAreForwardedOneAtATime() {
        assertEquals(RawInputDelta(0, " "), rawInputDelta("cmd", "cmd "))
        assertEquals(RawInputDelta(0, " "), rawInputDelta("cmd ", "cmd  "))
    }

    @Test
    fun softKeyboardBackspaceProducesDelete() {
        assertEquals(RawInputDelta(1, ""), rawInputDelta("cmd ", "cmd"))
    }
}
