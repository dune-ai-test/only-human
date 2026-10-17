package com.only.human.world

import androidx.compose.ui.graphics.Color

/** A person in the network. Fifteen of them exist; exactly one is human. */
data class Persona(
    val id: String,
    val name: String,
    val handle: String,          // without the @
    val bio: String,
    /** Short writing-style note. Seeds the LLM persona prompt. */
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

/** You. The one human — named during onboarding. */
data class Me(
    val name: String,
    val handle: String,
    val avatarIndex: Int = 0,
)

/** A post. Text only — this network has no photos. */
data class Post(
    val id: String,
    val authorId: String,
    val text: String,
    /** Epoch millis. The label is derived, so it ages honestly across visits. */
    val createdAt: Long,
    val likes: Int,
    val replies: Int,
    val likedByYou: Boolean = false,
) {
    val timeLabel: String get() = relativeTime(createdAt)
}

/** A reply under a post. */
data class Reply(
    val id: String,
    val postId: String,
    val authorId: String,
    val text: String,
    /** Epoch millis. */
    val createdAt: Long,
) {
    val timeLabel: String get() = relativeTime(createdAt)
}

/** "now" · "3m" · "2h" · "1d" · "3w" — the way the feed shows time. */
fun relativeTime(then: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - then) / 1000L).coerceAtLeast(0L)
    return when {
        seconds < 60 -> "now"
        seconds < 3_600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3_600}h"
        seconds < 604_800 -> "${seconds / 86_400}d"
        else -> "${seconds / 604_800}w"
    }
}

/** One row in the Activity tab. */
data class ActivityItem(
    val id: String,
    val personaId: String,
    val boldPart: String,
    val rest: String,
    /** Epoch millis. */
    val createdAt: Long,
    val kind: ActivityKind,
) {
    val timeLabel: String get() = relativeTime(createdAt)
}

enum class ActivityKind { Follow, Like, Reply, Mention, Message, System }

/** A direct message — one side of a private thread. */
data class Dm(
    val id: String,
    val personaId: String,
    /** true when you wrote it. */
    val fromMe: Boolean,
    val text: String,
    /** Epoch millis. */
    val at: Long,
) {
    val timeLabel: String get() = relativeTime(at)
}

/** A conversation shown in the Messages list. */
data class DmThread(
    val personaId: String,
    val last: Dm,
    val unread: Int,
)
