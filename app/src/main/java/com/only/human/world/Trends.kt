package com.only.human.world

/**
 * What "Trending" means here: a handful of themes, matched against what
 * the cast actually wrote. No fake numbers — a tag either has posts or it
 * doesn't, and the count always comes from the feed on your phone.
 */
object Trends {

    /** tag -> words that count as being about it. */
    val all: List<Pair<String, List<String>>> = listOf(
        "#nightshift" to listOf("night", "3am", "2am", "midnight", "insomnia", "late shift"),
        "#morningpeople" to listOf("morning", "sunrise", "early", "first light"),
        "#quietweeks" to listOf("quiet", "silence", "still", "slow", "hush"),
        "#homemadedal" to listOf("kitchen", "cook", "soup", "simmer", "stew", "bake", "dinner", "dal"),
        "#nowrongchords" to listOf("guitar", "chord", "piano", "music", "song", "melody", "hum"),
    )

    fun keywords(tag: String): List<String> =
        all.firstOrNull { it.first == tag }?.second ?: emptyList()

    /** The posts under a tag, newest first. */
    fun posts(tag: String): List<Post> {
        val keys = keywords(tag)
        if (keys.isEmpty()) return emptyList()
        return World.posts.filter { post ->
            val text = post.text.lowercase()
            keys.any { text.contains(it) }
        }
    }

    /** "3 posts" · "1 post" · "quiet under this tag for now". */
    fun label(tag: String): String = when (val n = posts(tag).size) {
        0 -> "nothing yet under this tag"
        1 -> "1 post"
        else -> "$n posts"
    }
}
