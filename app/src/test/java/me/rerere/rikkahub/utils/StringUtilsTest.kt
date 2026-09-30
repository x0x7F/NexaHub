package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StringUtilsTest {

    @Test
    fun `extract chinese double quotes`() {
        assertEquals(listOf("hello"), "He said “hello”".extractQuotedContent())
    }

    @Test
    fun `extract chinese single quotes`() {
        assertEquals(listOf("world"), "The title is ‘world’".extractQuotedContent())
    }

    @Test
    fun `extract english double quotes`() {
        assertEquals(listOf("hello"), "he said \"hello\"".extractQuotedContent())
    }

    @Test
    fun `extract english single quotes`() {
        assertEquals(listOf("world"), "title is 'world'".extractQuotedContent())
    }

    @Test
    fun `extract corner brackets`() {
        assertEquals(listOf("hello"), "He said 「hello」".extractQuotedContent())
    }

    @Test
    fun `extract white corner brackets`() {
        assertEquals(listOf("world"), "The title is 『world』".extractQuotedContent())
    }

    @Test
    fun `extract multiple quotes`() {
        assertEquals(
            listOf("hello", "world"),
            "“hello” and ‘world’".extractQuotedContent(),
        )
    }

    @Test
    fun `blank content is ignored`() {
        assertTrue("“” \"\" '  '".extractQuotedContent().isEmpty())
    }

    @Test
    fun `no quotes returns empty`() {
        assertTrue("No quotes at all".extractQuotedContent().isEmpty())
    }

    @Test
    fun `extract as text joins with separator`() {
        assertEquals("hello\nworld", "“hello”‘world’".extractQuotedContentAsText())
    }

    @Test
    fun `extract as text returns null when empty`() {
        assertNull("No quotes".extractQuotedContentAsText())
    }

    @Test
    fun `remove english brackets`() {
        assertEquals("helloworld", "hello(aside)world".removeBracketedContent())
    }

    @Test
    fun `remove chinese brackets`() {
        assertEquals("helloworld", "hello（aside）world".removeBracketedContent())
    }

    @Test
    fun `remove multiple brackets`() {
        assertEquals("helloworld", "hello(note)world（remark）".removeBracketedContent())
    }

    @Test
    fun `remove brackets keeps outside text trimmed`() {
        assertEquals("hello", "(aside) hello ".removeBracketedContent())
    }

    @Test
    fun `remove brackets does not cross bracket boundaries`() {
        assertEquals("ac", "a(b)c".removeBracketedContent())
    }

    @Test
    fun `remove brackets returns null when all removed`() {
        assertNull("(all aside)".removeBracketedContent())
    }

    @Test
    fun `remove brackets returns null for blank result`() {
        assertNull("（aside） ".removeBracketedContent())
    }

    @Test
    fun `no brackets returns original text`() {
        assertEquals("No brackets", "No brackets".removeBracketedContent())
    }
}
