package com.helga.android

import com.helga.android.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UiTextTest {

    @Test fun rawEquality() = assertEquals(UiText.Raw("x"), UiText.Raw("x"))

    @Test fun rawInequality() = assertNotEquals(UiText.Raw("x"), UiText.Raw("y"))

    @Test fun resEqualityWithArgs() =
        assertEquals(UiText.Res(1, listOf("a", 2)), UiText.Res(1, listOf("a", 2)))

    @Test fun resInequalityWithDifferentArgs() =
        assertNotEquals(UiText.Res(1, listOf("a")), UiText.Res(1, listOf("b")))
}
