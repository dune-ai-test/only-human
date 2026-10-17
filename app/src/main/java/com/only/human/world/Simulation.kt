package com.only.human.world

import kotlin.random.Random

/**
 * The catch-up engine: what the fifteen did while you were away.
 *
 * It only decides *who* did *what, when* — the words themselves are written
 * for real afterwards, one live call per post, reply, mention and DM. No
 * canned lines live here; if a write fails, that moment quietly doesn't
 * happen. Bounded — they are fifteen people, not a content farm — and
 * idempotent: the caller schedules the intents once and moves the "last
 * visit" marker forward.
 */
object Simulation {

    /** Someone posted — the text gets written for real later. */
    class PostIntent(val personaId: String, val at: Long)

    /** Someone replied to one of your posts — text written later. */
    class ReplyIntent(val personaId: String, val postId: String, val at: Long)

    /** Someone sent you a DM — text written later. */
    class DmIntent(val personaId: String, val at: Long)

    class Result {
        /** Existing posts whose like counts moved — no new text involved. */
        val edited = mutableListOf<Post>()

        /** Activity rows that only reference text that already exists. */
        val activities = mutableListOf<ActivityItem>()

        /** Personas who started following you. */
        val followsYou = mutableMapOf<String, Boolean>()

        /** Posts that need real text written before they can exist. */
        val posts = mutableListOf<PostIntent>()

        /** Replies to your posts that need real text. */
        val replies = mutableListOf<ReplyIntent>()

        /** A post that mentions you, needing real text. */
        var mention: PostIntent? = null

        /** DMs that need real text. */
        val dms = mutableListOf<DmIntent>()

        val isEmpty: Boolean
            get() = edited.isEmpty() && activities.isEmpty() && followsYou.isEmpty() &&
                posts.isEmpty() && replies.isEmpty() && mention == null && dms.isEmpty()
    }

    /** How chatty each of them is, relative to the others. */
    private val chatty = mapOf(
        "kai" to 0.2, "rosa" to 0.55, "sam" to 0.75, "ben" to 0.85, "lena" to 0.9,
        "theo" to 1.15, "sofia" to 1.25, "nina" to 1.25,
    )

    /**
     * [from] — when you left. [to] — now, when you came back.
     * [followedYou] — personaId -> do they already follow you.
     */
    fun catchUp(
        from: Long,
        to: Long,
        allPosts: List<Post>,
        followedYou: Map<String, Boolean>,
        rng: Random,
    ): Result {
        val result = Result()
        val gap = (to - from).toDouble()
        val hours = gap / 3_600_000.0
        if (hours < 0.75) return result

        val mine = allPosts.filter { it.authorId == "you" }
        val theirs = allPosts.filter { it.authorId != "you" }
        val updated = allPosts.associateBy { it.id }.toMutableMap()
        val touched = mutableSetOf<String>()

        // --- new posts: roughly one per 1.5 hours of absence, capped at eight
        val want = ((hours / 1.6) + rng.nextDouble()).toInt().coerceIn(0, 8)
        var made = 0
        val pool = Cast.people.shuffled(rng)
        var i = 0
        while (made < want && i < pool.size * 2) {
            val who = pool[i % pool.size]
            i++
            if (rng.nextDouble() > (chatty[who.id] ?: 1.0)) continue
            val at = from + (gap * rng.nextDouble()).toLong()
            result.posts += PostIntent(who.id, at)
            made++
        }

        // --- likes on what already exists; likes on your posts notify you
        val seen = mutableSetOf<Pair<String, String>>()
        val likeBursts = ((hours / 2.2) + rng.nextDouble()).toInt().coerceIn(1, 6)
        repeat(likeBursts) {
            val target = when {
                mine.isNotEmpty() && rng.nextDouble() < 0.55 -> mine.random(rng)
                theirs.isNotEmpty() -> theirs.random(rng)
                else -> null
            } ?: return@repeat
            val who = Cast.people.random(rng)
            if (!seen.add(target.id to who.id)) return@repeat
            val before = updated.getValue(target.id)
            updated[target.id] = before.copy(likes = before.likes + 1 + rng.nextInt(3))
            touched += target.id
            if (target.authorId == "you") {
                val at = from + (gap * rng.nextDouble()).toLong()
                result.activities += ActivityItem(
                    id = "sim-like-${target.id}-${who.id}",
                    personaId = who.id,
                    boldPart = who.name,
                    rest = " liked your post: \u201C${snippet(target.text)}\u201D",
                    createdAt = at,
                    kind = ActivityKind.Like,
                )
            }
        }

        // --- a new follower, if the gap was long enough for a first impression
        if (hours > 2.0 && rng.nextDouble() < 0.45) {
            val notYet = Cast.people.filter { followedYou[it.id] != true }
            if (notYet.isNotEmpty()) {
                val who = notYet.random(rng)
                val at = from + (gap * rng.nextDouble()).toLong()
                result.followsYou[who.id] = true
                result.activities += ActivityItem(
                    id = "sim-follow-${who.id}-$at",
                    personaId = who.id,
                    boldPart = who.name,
                    rest = " started following you",
                    createdAt = at,
                    kind = ActivityKind.Follow,
                )
            }
        }

        // --- replies to something you posted — written for real, later
        if (hours > 2.0 && mine.isNotEmpty()) {
            val replies = when {
                rng.nextDouble() < 0.35 -> 2
                rng.nextDouble() < 0.5 -> 1
                else -> 0
            }
            val used = mutableSetOf<String>()
            repeat(replies) {
                val who = Cast.people.filter { used.add(it.id) }.random(rng)
                val target = mine.random(rng)
                val at = (from + gap * (0.25 + 0.7 * rng.nextDouble())).toLong()
                result.replies += ReplyIntent(who.id, target.id, at)
            }
        }

        // --- a mention, occasionally — never from kai, he'd never
        if (hours > 2.0 && mine.isNotEmpty() && rng.nextDouble() < 0.5) {
            val who = Cast.people.filter { it.id != "kai" }.random(rng)
            val at = (from + gap * (0.4 + 0.55 * rng.nextDouble())).toLong()
            result.mention = PostIntent(who.id, at)
        }

        // --- a message or two: not everything belongs in public
        var sends = 0
        for (p in Cast.people.shuffled(rng)) {
            if (sends >= 2) break
            val chance = (0.10 + (hours / 100.0)).coerceAtMost(0.45)
            if (rng.nextDouble() > chance) continue
            val at = (from + gap * (0.3 + 0.6 * rng.nextDouble())).toLong()
            result.dms += DmIntent(p.id, at)
            sends++
        }

        result.edited += touched.mapNotNull { updated[it] }
        return result
    }

    private fun snippet(text: String): String {
        val flat = text.replace('\n', ' ')
        return if (flat.length <= 42) flat else flat.take(41).trimEnd() + "\u2026"
    }
}
