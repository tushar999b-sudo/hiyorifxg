package com.example

import com.example.avatar.HiyoriExpression
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `test Hiyori expressions parsing`() {
        assertEquals(HiyoriExpression.HAPPY, HiyoriExpression.fromString("HAPPY"))
        assertEquals(HiyoriExpression.SHY, HiyoriExpression.fromString("shy"))
        assertEquals(HiyoriExpression.SMUG, HiyoriExpression.fromString("SMUG"))
        assertEquals(HiyoriExpression.NEUTRAL, HiyoriExpression.fromString(null))
        assertEquals(HiyoriExpression.EXCITED, HiyoriExpression.fromString("EXCITED"))
        assertEquals(HiyoriExpression.LOVE, HiyoriExpression.fromString("LOVE"))
        assertEquals(HiyoriExpression.SLEEPY, HiyoriExpression.fromString("SLEEPY"))
    }

    @Test
    fun `test Hiyori expression sentiment detection`() {
        assertEquals(HiyoriExpression.HAPPY, HiyoriExpression.detectFromText("I'm so glad to see you!"))
        assertEquals(HiyoriExpression.SHY, HiyoriExpression.detectFromText("I blush whenever you compliment me"))
        assertEquals(HiyoriExpression.SLEEPY, HiyoriExpression.detectFromText("I'm so tired, goodnight!"))
        assertEquals(HiyoriExpression.LOVE, HiyoriExpression.detectFromText("I love you so much!"))
        assertEquals(HiyoriExpression.EXCITED, HiyoriExpression.detectFromText("That is awesome!! Let's go!"))
        assertEquals(HiyoriExpression.SAD, HiyoriExpression.detectFromText("I had a really bad day and feel sad"))
        assertEquals(HiyoriExpression.ANGRY, HiyoriExpression.detectFromText("Ugh this broken error is so annoying"))
        assertEquals(HiyoriExpression.SURPRISED, HiyoriExpression.detectFromText("Whoa, no way! Really?!"))
        assertEquals(HiyoriExpression.SMUG, HiyoriExpression.detectFromText("Obviously, we nailed it!"))
    }
}
