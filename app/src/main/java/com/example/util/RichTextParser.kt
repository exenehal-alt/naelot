package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.messageFontFamily

object RichTextParser {

    fun resolveHighlightColor(colorKey: String, defaultColor: Color): Color = when (colorKey) {
        "cyan" -> Color(0xFF38BDF8)
        "amber" -> Color(0xFFFBBF24)
        "emerald" -> Color(0xFF34D399)
        "rose" -> Color(0xFFFB7185)
        "violet" -> Color(0xFFA78BFA)
        else -> defaultColor
    }

    /**
     * Parses inline markdown tokens (**bold**, *italic*, __underline__, ~~strike~~, `code`, > quote)
     * and merges them with toolbar-level styling toggles and custom font family.
     */
    fun parseStyledMessage(
        rawText: String,
        fontFamilyKey: String,
        textColorKey: String,
        defaultColor: Color,
        forceBold: Boolean = false,
        forceItalic: Boolean = false,
        forceUnderline: Boolean = false,
        forceStrike: Boolean = false,
        forceCode: Boolean = false,
        forceQuote: Boolean = false,
        codeBgColor: Color = Color(0x331E293B)
    ): AnnotatedString {
        val baseColor = resolveHighlightColor(textColorKey, defaultColor)
        val baseFamily = if (forceCode) JetBrainsMonoFontFamily else messageFontFamily(fontFamilyKey)
        val decorations = mutableListOf<TextDecoration>()
        if (forceUnderline) decorations += TextDecoration.Underline
        if (forceStrike) decorations += TextDecoration.LineThrough

        val baseStyle = SpanStyle(
            color = baseColor,
            fontFamily = baseFamily,
            fontWeight = if (forceBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (forceItalic || forceQuote) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations),
            background = if (forceCode) codeBgColor else Color.Transparent,
            fontSize = if (forceCode) 13.5.sp else 15.sp
        )

        val cleanedLines = rawText.lines().joinToString("\n") { line ->
            if (line.startsWith("> ")) "❝ ${line.removePrefix("> ")}" else line
        }
        val text = if (forceQuote && !cleanedLines.startsWith("❝ ")) "❝ $cleanedLines" else cleanedLines

        val tokenRegex = Regex(
            """(\*\*(.+?)\*\*)|(__(.+?)__)|(~~(.+?)~~)|(`([^`]+?)`)|(\*(.+?)\*)"""
        )

        return buildAnnotatedString {
            pushStyle(baseStyle)
            var cursor = 0
            for (match in tokenRegex.findAll(text)) {
                if (match.range.first > cursor) {
                    append(text.substring(cursor, match.range.first))
                }
                when {
                    match.groups[2] != null -> {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(match.groups[2]!!.value)
                        pop()
                    }
                    match.groups[4] != null -> {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        append(match.groups[4]!!.value)
                        pop()
                    }
                    match.groups[6] != null -> {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        append(match.groups[6]!!.value)
                        pop()
                    }
                    match.groups[8] != null -> {
                        pushStyle(
                            SpanStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                background = codeBgColor,
                                fontSize = 13.5.sp
                            )
                        )
                        append(" ${match.groups[8]!!.value} ")
                        pop()
                    }
                    match.groups[10] != null -> {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(match.groups[10]!!.value)
                        pop()
                    }
                }
                cursor = match.range.last + 1
            }
            if (cursor < text.length) {
                append(text.substring(cursor))
            }
            pop()
        }
    }
}
