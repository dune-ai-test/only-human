package com.only.human.world

import androidx.compose.ui.graphics.Color

/** A person in the network. Fifteen of them exist; exactly one is human. */
data class Persona(
    val id: String,
    val name: String,
    val handle: String,          // without the @
    val bio: String,
    /** Short writing-style note. Seeds the LLM persona prompt later. */
    val voice: String,
    val gradient: List<Color>,
    val joinedYear: String,
    val followerBoost: Int,      // base follower count before the sim runs
) {
    val initials: String = run {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
    }
}

/** A post. Text only — this network has no photos. */
data class Post(
    val id: String,
    val authorId: String,
    val text: String,
    val timeLabel: String,
    val likes: Int,
    val replies: Int,
    val likedByYou: Boolean = false,
)

/** One row in the Activity tab. */
data class ActivityItem(
    val id: String,
    val personaId: String,
    val boldPart: String,
    val rest: String,
    val timeLabel: String,
    val kind: ActivityKind,
)

enum class ActivityKind { Follow, Like, Reply, Mention, System }
