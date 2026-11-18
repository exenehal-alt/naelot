package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.SpaceGroteskFontFamily

val avatarPresets = listOf(
    "preset:cyan" to listOf(Color(0xFF0EA5E9), Color(0xFF6366F1)),
    "preset:emerald" to listOf(Color(0xFF10B981), Color(0xFF06B6D4)),
    "preset:rose" to listOf(Color(0xFFF43F5E), Color(0xFFFB923C)),
    "preset:violet" to listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
    "preset:amber" to listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
    "preset:indigo" to listOf(Color(0xFF4F46E5), Color(0xFF0EA5E9))
)

fun decodeDataUrlBitmap(dataUrl: String): androidx.compose.ui.graphics.ImageBitmap? {
    if (!dataUrl.startsWith("data:image")) return null
    return try {
        val commaIdx = dataUrl.indexOf(',')
        if (commaIdx == -1) return null
        val bytes = Base64.decode(dataUrl.substring(commaIdx + 1), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
fun FriendAvatar(
    name: String,
    avatarData: String,
    size: Dp = 44.dp,
    showOnlineDot: Boolean = false,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) {
        name.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
    }
    val presetColors = remember(avatarData) {
        avatarPresets.firstOrNull { it.first == avatarData }?.second
            ?: listOf(Color(0xFF0284C7), Color(0xFF4F46E5))
    }
    val decodedBitmap = remember(avatarData) { decodeDataUrlBitmap(avatarData) }

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.linearGradient(presetColors)),
            contentAlignment = Alignment.Center
        ) {
            when {
                decodedBitmap != null -> {
                    Image(
                        bitmap = decodedBitmap,
                        contentDescription = "$name avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                avatarData.startsWith("http") -> {
                    AsyncImage(
                        model = avatarData,
                        contentDescription = "$name avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.36f).sp
                    )
                }
            }
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .size((size.value * 0.28f).dp.coerceAtLeast(10.dp))
                    .align(Alignment.BottomEnd)
                    .offset(x = 1.dp, y = 1.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) EmeraldOnline else MaterialTheme.colorScheme.outline)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}
