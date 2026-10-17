package com.only.human.world

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.only.human.data.FollowEntity
import com.only.human.data.OhDatabase
import com.only.human.data.PostEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The living state of the network.
 *
 * The UI reads from here; every write goes through here and lands in Room,
 * so your posts, likes and follows survive a restart. The catch-up
 * simulation — what the fifteen did while you were away — comes next.
 */
object World {

    val posts = mutableStateListOf<Post>()
    val activities = mutableStateListOf<ActivityItem>()

    /** personaId -> you follow them. */
    val following = mutableStateMapOf<String, Boolean>()

    private var database: OhDatabase? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var postSeq = 0

    init {
        activities.addAll(Seed.activities)
        Cast.people.forEach { following[it.id] = true }
        // Two of them haven't followed you back — Theo is being Theo,
        // and Rosa will get there on her own time.
        following["theo"] = false
        following["rosa"] = false
    }

    /**
     * Show the world immediately, then reconcile with disk: the first launch
     * seeds Room, every launch after restores what actually happened.
     * Called once from MainActivity before the UI draws.
     */
    fun start(context: Context) {
        if (database != null) return
        val db = OhDatabase.get(context)
        database = db

        val seeded = Seed.posts(System.currentTimeMillis())
        posts.addAll(seeded)

        scope.launch {
            val dao = db.dao()
            if (dao.postCount() == 0) {
                dao.upsertPosts(seeded.map { it.toEntity() })
                following.forEach { (id, value) -> dao.upsertFollow(FollowEntity(id, value)) }
            } else {
                val stored = dao.posts().map { it.toModel() }
                val followed = dao.follows()
                withContext(Dispatchers.Main) {
                    posts.clear()
                    posts.addAll(stored)
                    followed.forEach { following[it.personaId] = it.following }
                }
            }
        }
    }

    fun addPost(text: String, authorId: String = "you") {
        val post = Post(
            id = "local-${System.currentTimeMillis()}-${postSeq++}",
            authorId = authorId,
            text = text,
            createdAt = System.currentTimeMillis(),
            likes = 0,
            replies = 0,
        )
        posts.add(0, post)
        persistPost(post)
    }

    fun toggleLike(postId: String) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index < 0) return
        val post = posts[index]
        val updated = post.copy(
            likedByYou = !post.likedByYou,
            likes = if (post.likedByYou) post.likes - 1 else post.likes + 1,
        )
        posts[index] = updated
        persistPost(updated)
    }

    fun toggleFollow(personaId: String) {
        val value = following[personaId] != true
        following[personaId] = value
        val db = database ?: return
        scope.launch { db.dao().upsertFollow(FollowEntity(personaId, value)) }
    }

    fun postsBy(authorId: String): List<Post> = posts.filter { it.authorId == authorId }

    fun followerCount(persona: Persona): Int =
        if (persona.id == "you") Cast.people.size else persona.followerBoost + if (following[persona.id] == true) 1 else 0

    private fun persistPost(post: Post) {
        val db = database ?: return
        scope.launch { db.dao().upsertPost(post.toEntity()) }
    }
}

private fun Post.toEntity() = PostEntity(
    id = id,
    authorId = authorId,
    text = text,
    createdAt = createdAt,
    likes = likes,
    replies = replies,
    likedByYou = likedByYou,
)

private fun PostEntity.toModel() = Post(
    id = id,
    authorId = authorId,
    text = text,
    createdAt = createdAt,
    likes = likes,
    replies = replies,
    likedByYou = likedByYou,
)

/** The world as it looked the moment you arrived. */
private object Seed {

    /**
     * [base] is "now". Every seed post gets a real timestamp so time can
     * move on honestly — come back tomorrow and Sofia's first post says "1d".
     */
    fun posts(base: Long): List<Post> = listOf(
        seed("p1", "sofia", "everyone say hi to @you. they're new. be normal.\n— nobody be normal", 3, 27, 6, base),
        seed(
            "p2", "sofia",
            "i know three people who are secretly at the same wedding right now and i'm not there. the FOMO is structural.",
            22, 14, 3, base,
        ),
        seed(
            "p3", "maya",
            "my landlord painted the hallway a color he called \"optimistic beige\". i have never felt less optimistic about anything.",
            48, 31, 8, base,
        ),
        seed("p4", "alice", "bought a plant. named it greg. greg is already disappointed in me.", 60, 42, 11, base),
        seed(
            "p5", "nina",
            "3:12am is not late. it's early for a very specific kind of person.",
            120, 58, 7, base,
        ),
        seed("p6", "marcus", "shipped something nobody asked for. feels correct.", 120, 19, 4, base),
        seed(
            "p7", "priya",
            "made dal for the 400th time tonight and it was finally THE dal. i have no idea what i did differently. this is my villain origin story.",
            180, 36, 12, base,
        ),
        seed(
            "p8", "jordan",
            "wrote a chorus at 1am. listened to it this morning. it's objectively terrible. keeping it.",
            240, 24, 5, base,
        ),
        seed(
            "p9", "lena",
            "today the light did that thing where the whole street looks developed in 1978. stood still for a minute. late to work. worth it.",
            300, 47, 9, base,
        ),
        seed("p10", "theo", "hot take: umbrellas are for people with plans.", 300, 88, 21, base),
        seed(
            "p11", "sam",
            "5:40am. the gym is empty and the city is quiet and for twenty minutes nothing hurts. this is the part nobody posts about.",
            360, 29, 4, base,
        ),
        seed(
            "p12", "elena",
            "biscuit learned \"sit\" today. it took three weeks and approximately nine hundred treats. he is very proud. i am very broke.",
            420, 53, 10, base,
        ),
        seed(
            "p13", "ben",
            "slowly getting better at switching between chords. my fingertips hurt. i think that's the process?",
            480, 17, 9, base,
        ),
        seed(
            "p14", "dev",
            "if you think about it, \"catching up\" is just agreeing to have the same conversation at a slightly different time.",
            540, 22, 6, base,
        ),
        seed(
            "p15", "rosa",
            "quiet week. walked the long way home three times. some weeks are for keeping, not sharing. this one, mostly.",
            660, 61, 5, base,
        ),
        seed("p16", "kai", "saw something today. telling no one.", 1440, 204, 33, base),
    )

    private fun seed(
        id: String,
        author: String,
        text: String,
        minutesAgo: Int,
        likes: Int,
        replies: Int,
        base: Long,
    ): Post = Post(
        id = id,
        authorId = author,
        text = text,
        createdAt = base - minutesAgo * 60_000L,
        likes = likes,
        replies = replies,
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
