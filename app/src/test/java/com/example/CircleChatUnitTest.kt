package com.example

import androidx.compose.ui.graphics.Color
import com.example.data.model.ChatMessage
import com.example.util.RichTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleChatUnitTest {

    @Test
    fun richTextParser_parsesMarkdownTokens() {
        val annotated = RichTextParser.parseStyledMessage(
            rawText = "Hello **bold** *italic* __under__ ~~old~~ `code`",
            fontFamilyKey = "mono",
            textColorKey = "cyan",
            defaultColor = Color.White
        )
        assertTrue(annotated.text.contains("bold"))
        assertTrue(annotated.text.contains("italic"))
        assertTrue(annotated.text.contains("under"))
        assertTrue(annotated.text.contains("old"))
        assertTrue(annotated.text.contains("code"))
        assertTrue(!annotated.text.contains("**"))
        assertTrue(!annotated.text.contains("~~"))
    }

    @Test
    fun chatMessage_parsedPoll_handlesLongAndListCoercion() {
        val msg = ChatMessage(
            id = "m1",
            type = "poll",
            pollData = mapOf(
                "question" to "Movie night?",
                "allowMultiple" to true,
                "options" to listOf(
                    mapOf("id" to "opt_0", "text" to "Dune", "voterIds" to listOf("u1", "u2")),
                    mapOf("id" to "opt_1", "text" to "Interstellar", "voterIds" to listOf("u3"))
                )
            )
        )
        val poll = msg.parsedPoll()
        assertNotNull(poll)
        assertEquals("Movie night?", poll?.question)
        assertEquals(true, poll?.allowMultiple)
        assertEquals(2, poll?.options?.size)
        assertEquals(listOf("u1", "u2"), poll?.options?.get(0)?.voterIds)
    }
}
