package com.qolve.fluyo.data.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device regression test for the HU-05 voice parser.
 *
 * [VoiceParser] compiles its category regexes in a static initializer. A pattern that is
 * valid on the JVM but rejected by Android's ICU regex engine — e.g. the `(?U)` inline
 * flag — throws PatternSyntaxException inside <clinit>, so the app crashed the instant
 * parse() first ran (right after dictation returned RESULT_OK). The pure-JVM
 * [VoiceParserTest] structurally cannot catch that: desktop Java accepts `(?U)`. This test
 * exercises parse() on the real device engine, so any Android-incompatible regex regresses
 * here (ExceptionInInitializerError) instead of in production.
 */
@RunWith(AndroidJUnit4::class)
class VoiceParserInstrumentedTest {

    @Test
    fun parsesAccentedKeywordOnDeviceEngine() {
        val r = VoiceParser.parse("S/ 12.50 en café")
        assertEquals(12.50, r.amount!!, 0.001)
        assertEquals("Snacks", r.categoryHint)
        assertEquals("café", r.description)
    }

    @Test
    fun parsesFullPhraseOnDeviceEngine() {
        val r = VoiceParser.parse("gasté 15 soles en almuerzo")
        assertEquals(15.0, r.amount!!, 0.001)
        assertEquals("Comida", r.categoryHint)
        assertEquals("almuerzo", r.description)
    }

    @Test
    fun keywordInsideLongerWordDoesNotMatch() {
        // "comi" must not match inside "comida"/"comisaría": the \p{L} boundaries guard this.
        val r = VoiceParser.parse("40 en comisaría")
        assertNull(r.categoryHint)
        assertEquals("comisaría", r.description)
    }
}
