package com.example.androidkotlinapp.ui.main

/** A named group of emojis shown as one section of the picker. */
data class EmojiCategory(val name: String, val emojis: List<String>)

/**
 * Emoji choices for habit icons, grouped the way the picker presents them.
 *
 * Deliberately broad rather than a curated handful -- habits are personal, so the list
 * covers routine, study, fitness, food, faith and finance instead of guessing which few
 * someone will want.
 */
object EmojiCatalog {

    val categories: List<EmojiCategory> = listOf(
        EmojiCategory(
            "Routine & Time",
            listOf(
                "⏰", "⏳", "🌅", "🌄", "🌙", "🌞", "🛏️", "🚿", "🪥", "🧹",
                "🧺", "📅", "🗓️", "⌚", "🔔", "☀️", "🌇", "🕕", "🧴", "🪒"
            )
        ),
        EmojiCategory(
            "Study & Work",
            listOf(
                "📚", "📖", "✍️", "📝", "🧠", "💻", "⌨️", "🖥️", "📊", "📈",
                "🔬", "🧪", "🧮", "📐", "🎓", "🏫", "💼", "📎", "🗂️", "🔖",
                "✏️", "📓", "🖊️", "💡", "🧑‍💻"
            )
        ),
        EmojiCategory(
            "Fitness & Sport",
            listOf(
                "💪", "🏃", "🚶", "🧗", "🏋️", "🤸", "🚴", "🏊", "⚽", "🏀",
                "🏸", "🎾", "🏐", "🥊", "🥋", "⛹️", "🤾", "🏓", "🛹", "🧘",
                "🤺", "🏑", "🥅", "🚵", "🏆"
            )
        ),
        EmojiCategory(
            "Health & Care",
            listOf(
                "💧", "🥗", "🍎", "🥦", "🥕", "🍌", "🥛", "🫐", "🥚", "🍇",
                "💊", "🩺", "🫀", "🦷", "😴", "🚭", "🚫", "🧼", "🌿", "🍵",
                "🥤", "🧃", "🍽️", "⚖️", "🩹"
            )
        ),
        EmojiCategory(
            "Faith & Spirit",
            listOf(
                "🙏", "🕌", "🕋", "📿", "☪️", "🛐", "⛪", "✝️", "☦️", "🕍",
                "✡️", "🕉️", "☸️", "🛕", "🪔", "🧎", "🕯️", "☮️", "🔯", "⛩️",
                "📖", "🤲", "🧿", "🪬", "☯️"
            )
        ),
        EmojiCategory(
            "Mind & Mood",
            listOf(
                "🧘", "😌", "🙂", "😊", "🥰", "🤗", "😇", "🫶", "❤️", "💖",
                "🌸", "🍀", "✨", "🌈", "🎯", "🧩", "🎵", "🎧", "🎨", "📷",
                "🪷", "🌼", "😤", "🫧", "💭"
            )
        ),
        EmojiCategory(
            "Money & Goals",
            listOf(
                "💰", "💵", "🪙", "🏦", "📉", "💳", "🧾", "🎁", "🏅", "🥇",
                "🚀", "🔥", "⭐", "🌟", "🗝️", "🧭", "⛳", "🪜", "📌", "🏁"
            )
        ),
        EmojiCategory(
            "Home & Life",
            listOf(
                "🏠", "🪴", "🌱", "🐕", "🐈", "🧑‍🍳", "🍳", "🧽", "🛒", "🧳",
                "🚗", "✈️", "📞", "💬", "👨‍👩‍👧", "🤝", "📬", "🔧", "🪛", "🌳"
            )
        )
    )

    /** Flat list used when searching or picking a fallback. */
    val allEmojis: List<String> = categories.flatMap { it.emojis }.distinct()
}
