package com.only.human.world

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf

/**
 * The living state of the network. In-memory for now — Room persistence and
 * the catch-up simulation land in the next milestone. Everything the UI
 * reads goes through here so swapping in the store is a one-file change.
 */
object World {

    val posts = mutableStateListOf<Post>()
    val activities = mutableStateListOf<ActivityItem>()

    /** personaId -> you follow them. */
    val following = mutableStateMapOf<String, Boolean>()

    private var postSeq = 0

    init {
        posts.addAll(Seed.posts)
        activities.addAll(Seed.activities)
        Cast.people.forEach { following[it.id] = true }
        // Two of them haven't followed you back — Theo is being Theo,
        // and Rosa will get there on her own time.
        following["theo"] = false
        following["rosa"] = false
    }

    fun addPost(text: String, authorId: String = "you") {
        posts.add(
            0,
            Post(
                id = "local-${postSeq++}",
                authorId = authorId,
                text = text,
                timeLabel = "now",
                likes = 0,
                replies = 0,
            ),
        )
    }

    fun toggleLike(postId: String) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index < 0) return
        val post = posts[index]
        posts[index] = post.copy(
            likedByYou = !post.likedByYou,
            likes = if (post.likedByYou) post.likes - 1 else post.likes + 1,
        )
    }

    fun toggleFollow(personaId: String) {
        following[personaId] = following[personaId] != true
    }

    fun postsBy(authorId: String): List<Post> = posts.filter { it.authorId == authorId }

    fun followerCount(persona: Persona): Int =
        if (persona.id == "you") Cast.people.size else persona.followerBoost + if (following[persona.id] == true) 1 else 0
}

/** The world as it looked the moment you arrived. */
private object Seed {

    val posts: List<Post> = listOf(
        Post("p1", "sofia", "everyone say hi to @you. they're new. be normal.\n— nobody be normal", "3m", 27, 6),
        Post(
            "p2", "sofia",
            "i know three people who are secretly at the same wedding right now and i'm not there. the FOMO is structural.",
            "22m", 14, 3,
        ),
        Post(
            "p3", "maya",
            "my landlord painted the hallway a color he called \"optimistic beige\". i have never felt less optimistic about anything.",
            "48m", 31, 8,
        ),
        Post("p4", "alice", "bought a plant. named it greg. greg is already disappointed in me.", "1h", 42, 11),
        Post(
            "p5", "nina",
            "3:12am is not late. it's early for a very specific kind of person.",
            "2h", 58, 7,
        ),
        Post("p6", "marcus", "shipped something nobody asked for. feels correct.", "2h", 19, 4),
        Post(
            "p7", "priya",
            "made dal for the 400th time tonight and it was finally THE dal. i have no idea what i did differently. this is my villain origin story.",
            "3h", 36, 12,
        ),
        Post(
            "p8", "jordan",
            "wrote a chorus at 1am. listened to it this morning. it's objectively terrible. keeping it.",
            "4h", 24, 5,
        ),
        Post(
            "p9", "lena",
            "today the light did that thing where the whole street looks developed in 1978. stood still for a minute. late to work. worth it.",
            "5h", 47, 9,
        ),
        Post("p10", "theo", "hot take: umbrellas are for people with plans.", "5h", 88, 21),
        Post(
            "p11", "sam",
            "5:40am. the gym is empty and the city is quiet and for twenty minutes nothing hurts. this is the part nobody posts about.",
            "6h", 29, 4,
        ),
        Post(
            "p12", "elena",
            "biscuit learned \"sit\" today. it took three weeks and approximately nine hundred treats. he is very proud. i am very broke.",
            "7h", 53, 10,
        ),
        Post(
            "p13", "ben",
            "slowly getting better at switching between chords. my fingertips hurt. i think that's the process?",
            "8h", 17, 9,
        ),
        Post(
            "p14", "dev",
            "if you think about it, \"catching up\" is just agreeing to have the same conversation at a slightly different time.",
            "9h", 22, 6,
        ),
        Post(
            "p15", "rosa",
            "quiet week. walked the long way home three times. some weeks are for keeping, not sharing. this one, mostly.",
            "11h", 61, 5,
        ),
        Post("p16", "kai", "saw something today. telling no one.", "1d", 204, 33),
    )

    val activities: List<ActivityItem> = listOf(
        ActivityItem("a1", "sofia", "Sofia Marchetti", " started following you", "8m", ActivityKind.Follow),
        ActivityItem("a2", "sofia", "Sofia Marchetti", " mentioned you in a post", "10m", ActivityKind.Mention),
        ActivityItem("a3", "marcus", "Marcus Bell", " started following you", "45m", ActivityKind.Follow),
        ActivityItem("a4", "elena", "Elena Ruiz", " started following you", "1h", ActivityKind.Follow),
        ActivityItem("a5", "nina", "Nina Petrova", " started following you", "2h", ActivityKind.Follow),
        ActivityItem("a6", "kai", "Kai Tanaka", " started following you. that basically never happens", "3h", ActivityKind.Follow),
        ActivityItem("a7", "you", "Welcome to Only Human", " — fifteen people just got a new neighbor.", "today", ActivityKind.System),
    )
}
