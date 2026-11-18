package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RichTextStyleState
import com.example.ui.theme.messageFontFamily

private val fontOptions = listOf(
    "sans" to "Sans",
    "serif" to "Serif",
    "mono" to "Mono",
    "script" to "Script",
    "display" to "Display"
)

private val colorOptions = listOf(
    "default" to Color(0xFF94A3B8),
    "cyan" to Color(0xFF38BDF8),
    "amber" to Color(0xFFFBBF24),
    "emerald" to Color(0xFF34D399),
    "rose" to Color(0xFFFB7185),
    "violet" to Color(0xFFA78BFA)
)

@Composable
fun RichTextToolbar(
    styleState: RichTextStyleState,
    onUpdateStyle: ((RichTextStyleState) -> RichTextStyleState) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Font Families Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                fontOptions.forEach { (key, label) ->
                    val selected = styleState.fontFamily == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { onUpdateStyle { it.copy(fontFamily = key) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("font_chip_$key")
                    ) {
                        Text(
                            text = label,
                            fontFamily = messageFontFamily(key),
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Text Style Toggles + Highlight Colors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyleToggleButton(
                    icon = Icons.Default.FormatBold,
                    contentDesc = "Bold",
                    active = styleState.bold,
                    tag = "style_bold",
                    onClick = { onUpdateStyle { it.copy(bold = !it.bold) } }
                )
                StyleToggleButton(
                    icon = Icons.Default.FormatItalic,
                    contentDesc = "Italic",
                    active = styleState.italic,
                    tag = "style_italic",
                    onClick = { onUpdateStyle { it.copy(italic = !it.italic) } }
                )
                StyleToggleButton(
                    icon = Icons.Default.FormatUnderlined,
                    contentDesc = "Underline",
                    active = styleState.underline,
                    tag = "style_underline",
                    onClick = { onUpdateStyle { it.copy(underline = !it.underline) } }
                )
                StyleToggleButton(
                    icon = Icons.Default.FormatStrikethrough,
                    contentDesc = "Strikethrough",
                    active = styleState.strikethrough,
                    tag = "style_strike",
                    onClick = { onUpdateStyle { it.copy(strikethrough = !it.strikethrough) } }
                )
                StyleToggleButton(
                    icon = Icons.Default.Code,
                    contentDesc = "Inline code",
                    active = styleState.inlineCode,
                    tag = "style_code",
                    onClick = { onUpdateStyle { it.copy(inlineCode = !it.inlineCode) } }
                )
                StyleToggleButton(
                    icon = Icons.Default.FormatQuote,
                    contentDesc = "Blockquote",
                    active = styleState.blockquote,
                    tag = "style_quote",
                    onClick = { onUpdateStyle { it.copy(blockquote = !it.blockquote) } }
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = 1.dp, height = 22.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )

                colorOptions.forEach { (key, color) ->
                    val selected = styleState.textColorKey == key
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onUpdateStyle { it.copy(textColorKey = key) } }
                            .testTag("color_dot_$key")
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleToggleButton(
    icon: ImageVector,
    contentDesc: String,
    active: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else Color.Transparent
            )
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp)
        )
    }
}
