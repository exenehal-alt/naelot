package com.example.data.media

data class GifItem(
    val id: String,
    val title: String,
    val category: String,
    val previewUrl: String,
    val emojiBadge: String,
    val accentHex: Long
)

object GifCatalog {
    val categories = listOf("All", "Reactions", "Celebrate", "Funny", "Love", "Gaming")

    val items = listOf(
        GifItem("gif_mindblown", "Mind Blown", "Reactions", "https://media.giphy.com/media/xT0xeJpnrWC4XWblEk/giphy.gif", "🤯", 0xFF38BDF8),
        GifItem("gif_clap", "Standing Ovation", "Celebrate", "https://media.giphy.com/media/l3q2XhfQ8oCkm1Ts4/giphy.gif", "👏", 0xFFFBBF24),
        GifItem("gif_party", "Party Popper", "Celebrate", "https://media.giphy.com/media/26tOZ42Mg6pbTUPHW/giphy.gif", "🎉", 0xFF34D399),
        GifItem("gif_laugh", "Can't Stop Laughing", "Funny", "https://media.giphy.com/media/10JhviFuU2gWD6/giphy.gif", "😂", 0xFFFB7185),
        GifItem("gif_cat", "Coding Cat", "Gaming", "https://media.giphy.com/media/LHZyixOnHwDDy/giphy.gif", "💻", 0xFF818CF8),
        GifItem("gif_hearts", "Sending Love", "Love", "https://media.giphy.com/media/26FLdmIp6wJr91JAI/giphy.gif", "❤️", 0xFFF43F5E),
        GifItem("gif_fire", "That's Fire", "Reactions", "https://media.giphy.com/media/3o72FfM5HJydzafgUE/giphy.gif", "🔥", 0xFFF97316),
        GifItem("gif_gg", "GG Well Played", "Gaming", "https://media.giphy.com/media/3o6fJ1BM7R2EBRDnxK/giphy.gif", "🎮", 0xFF22D3EE),
        GifItem("gif_coffee", "Coffee Time", "Funny", "https://media.giphy.com/media/3oriO04qxVReM5rJEA/giphy.gif", "☕", 0xFFA78BFA),
        GifItem("gif_thumbsup", "Approve 100%", "Reactions", "https://media.giphy.com/media/111ebonMs90YLu/giphy.gif", "👍", 0xFF10B981),
        GifItem("gif_hug", "Big Hug", "Love", "https://media.giphy.com/media/llmZp6fCVb4ju/giphy.gif", "🤗", 0xFFEC4899),
        GifItem("gif_dance", "Victory Dance", "Celebrate", "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif", "🕺", 0xFF6366F1)
    )

    val emojiCategories = linkedMapOf(
        "Frequent" to listOf("❤️", "😂", "🔥", "👍", "😮", "😢", "🎉", "🚀", "⚡", "🙌", "👀", "💯"),
        "Smileys" to listOf("😀", "😄", "😁", "🤣", "😊", "😇", "😉", "😍", "🥰", "🤩", "😎", "🥳", "🤔", "🤯", "😴", "😭"),
        "Hands" to listOf("👋", "✌️", "🤞", "🤙", "👌", "👊", "👏", "🙏", "💪", "🤝", "🫶", "🫡"),
        "Vibes" to listOf("✨", "🌟", "💫", "🌈", "🎵", "🎧", "🎮", "📸", "🎬", "🍕", "☕", "🏆")
    )

    val quickReactions = listOf("❤️", "😂", "👍", "🔥", "😮", "😢")

    fun searchGifs(query: String, category: String): List<GifItem> {
        val q = query.trim().lowercase()
        return items.filter { item ->
            val catOk = category == "All" || item.category.equals(category, ignoreCase = true)
            val queryOk = q.isEmpty() || item.title.lowercase().contains(q) || item.category.lowercase().contains(q)
            catOk && queryOk
        }
    }
}
