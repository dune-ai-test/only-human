package com.only.human.world

import kotlin.random.Random

/**
 * The catch-up engine: what the fifteen did while you were away.
 *
 * Bounded (they are fifteen people, not a content farm), written in their own
 * voices via [Voices], and idempotent — the caller persists the result and
 * moves the "last visit" marker forward, so a given absence simulates once.
 */
object Simulation {

    class Result {
        /** New posts they wrote while you were gone. */
        val posts = mutableListOf<Post>()

        /** Existing posts whose like/reply counts moved. */
        val edited = mutableListOf<Post>()

        /** Activity-tab rows worth telling you about. */
        val activities = mutableListOf<ActivityItem>()

        /** Personas who started following you. */
        val followsYou = mutableMapOf<String, Boolean>()

        /** Private messages that arrived while you were away. */
        val dms = mutableListOf<Dm>()

        val isEmpty: Boolean
            get() = posts.isEmpty() && edited.isEmpty() && activities.isEmpty() &&
                followsYou.isEmpty() && dms.isEmpty()
    }

    /** How chatty each of them is, relative to the others. */
    private val chatty = mapOf(
        "kai" to 0.2, "rosa" to 0.55, "sam" to 0.75, "ben" to 0.85, "lena" to 0.9,
        "theo" to 1.15, "sofia" to 1.25, "nina" to 1.25,
    )

    /**
     * [from] — when you left. [to] — now, when you came back.
     * [followedYou] — personaId -> do they already follow you.
     * [myHandle] — your username, so mentions say the right name.
     */
    fun catchUp(
        from: Long,
        to: Long,
        allPosts: List<Post>,
        followedYou: Map<String, Boolean>,
        myHandle: String,
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
            result.posts += Post(
                id = "sim-${at}-${who.id}-${rng.nextInt(100_000)}",
                authorId = who.id,
                text = Voices.post(who.id, at, rng),
                createdAt = at,
                likes = 4 + rng.nextInt(24) + who.followerBoost / 24,
                replies = rng.nextInt(5),
            )
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

        // --- replies to something you posted
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
                val before = updated.getValue(target.id)
                updated[target.id] = before.copy(replies = before.replies + 1)
                touched += target.id
                result.activities += ActivityItem(
                    id = "sim-reply-${target.id}-${who.id}",
                    personaId = who.id,
                    boldPart = who.name,
                    rest = " replied: \u201C${Voices.reply(rng)}\u201D",
                    createdAt = at,
                    kind = ActivityKind.Reply,
                )
            }
        }

        // --- a mention, occasionally — never from kai, he'd never
        if (hours > 2.0 && mine.isNotEmpty() && rng.nextDouble() < 0.5) {
            val who = Cast.people.filter { it.id != "kai" }.random(rng)
            val at = (from + gap * (0.4 + 0.55 * rng.nextDouble())).toLong()
            result.posts += Post(
                id = "sim-mention-${at}-${who.id}",
                authorId = who.id,
                text = Voices.mention(rng).replace("@you", "@$myHandle"),
                createdAt = at,
                likes = 6 + rng.nextInt(30) + who.followerBoost / 30,
                replies = rng.nextInt(3),
            )
            result.activities += ActivityItem(
                id = "sim-mention-a-$at",
                personaId = who.id,
                boldPart = who.name,
                rest = " mentioned you in a post",
                createdAt = at,
                kind = ActivityKind.Mention,
            )
        }

        // --- a message or two: not everything belongs in public
        var sends = 0
        for (p in Cast.people.shuffled(rng)) {
            if (sends >= 2) break
            val chance = (0.10 + (hours / 100.0)).coerceAtMost(0.45)
            if (rng.nextDouble() > chance) continue
            val at = (from + gap * (0.3 + 0.6 * rng.nextDouble())).toLong()
            result.dms += Dm(
                id = "sim-dm-$at-${p.id}",
                personaId = p.id,
                fromMe = false,
                text = Voices.dmOpen(p.id, rng),
                at = at,
            )
            result.activities += ActivityItem(
                id = "sim-dm-a-$at-${p.id}",
                personaId = p.id,
                boldPart = p.name,
                rest = " sent you a message",
                createdAt = at,
                kind = ActivityKind.Message,
            )
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
