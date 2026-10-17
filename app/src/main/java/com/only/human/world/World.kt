package com.only.human.world

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.only.human.data.ActivityEntity
import com.only.human.data.FollowEntity
import com.only.human.data.OhDao
import com.only.human.data.OhDatabase
import com.only.human.data.PostEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * The living state of the network.
 *
 * The UI reads from here; every write goes through here into Room. Coming
 * back after a gap runs the catch-up simulation: the fifteen post, reply and
 * follow while you're gone, and the feed and Activity tab show what you
 * missed — all in their own voices, no LLM required.
 */
object World {

    val posts = mutableStateListOf<Post>()
    val activities = mutableStateListOf<ActivityItem>()

    /** personaId -> you follow them. */
    val following = mutableStateMapOf<String, Boolean>()

    /** personaId -> they follow you. */
    val followedBy = mutableStateMapOf<String, Boolean>()

    private var database: OhDatabase? = null
    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var postSeq = 0

    private const val PREFS = "only_human"
    private const val KEY_LAST_VISIT = "lastVisitAt"

    init {
        Cast.people.forEach {
            following[it.id] = true
            followedBy[it.id] = true
        }
        // Two of them haven't followed you back — Theo is being Theo,
        // and Rosa will get there on her own time.
        following["theo"] = false
        following["rosa"] = false
        followedBy["theo"] = false
        followedBy["rosa"] = false
    }

    /**
     * Show the world immediately, then reconcile with disk: the first launch
     * seeds Room, every launch after restores what actually happened — and
     * simulates the time you were away.
     */
    fun start(context: Context) {
        if (database != null) return
        val db = OhDatabase.get(context)
        database = db
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val base = System.currentTimeMillis()
        posts.addAll(Seed.posts(base))
        activities.addAll(Seed.activities(base))

        scope.launch {
            val dao = db.dao()
            val firstRun = dao.postCount() == 0
            if (firstRun) {
                dao.upsertPosts(posts.map { it.toEntity() })
                dao.upsertActivities(activities.map { it.toEntity() })
                following.forEach { (id, value) ->
                    dao.upsertFollow(FollowEntity(id, value, followedBy[id] == true))
                }
            }

            val lastVisit = prefs?.getLong(KEY_LAST_VISIT, 0L) ?: 0L
            val now = System.currentTimeMillis()
            if (!firstRun && lastVisit in 1 until now) {
                runCatchUp(dao, lastVisit, now)
            }

            val storedPosts = dao.posts().map { it.toModel() }
            val storedActivities = dao.activities().map { it.toModel() }
            val storedFollows = dao.follows()
            withContext(Dispatchers.Main) {
                posts.clear()
                posts.addAll(storedPosts)
                activities.clear()
                activities.addAll(storedActivities)
                storedFollows.forEach {
                    following[it.personaId] = it.following
                    followedBy[it.personaId] = it.followsYou
                }
            }
            prefs?.edit()?.putLong(KEY_LAST_VISIT, System.currentTimeMillis())?.apply()
        }
    }

    /**
     * Called when the app goes to the background. The next launch simulates
     * the gap between this moment and then.
     */
    fun markAway() {
        prefs?.edit()?.putLong(KEY_LAST_VISIT, System.currentTimeMillis())?.apply()
    }

    private suspend fun runCatchUp(dao: OhDao, from: Long, to: Long) {
        val followRows = dao.follows()
        val result = Simulation.catchUp(
            from = from,
            to = to,
            allPosts = dao.posts().map { it.toModel() },
            followedYou = followRows.associate { it.personaId to it.followsYou },
            rng = Random(from xor to),
        )
        if (result.isEmpty) return
        if (result.posts.isNotEmpty()) dao.upsertPosts(result.posts.map { it.toEntity() })
        if (result.edited.isNotEmpty()) dao.upsertPosts(result.edited.map { it.toEntity() })
        if (result.activities.isNotEmpty()) dao.upsertActivities(result.activities.map { it.toEntity() })
        result.followsYou.forEach { (id, value) ->
            val mine = followRows.firstOrNull { it.personaId == id }?.following ?: (following[id] == true)
            dao.upsertFollow(FollowEntity(id, mine, value))
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
        scope.launch {
            db.dao().upsertFollow(FollowEntity(personaId, value, followedBy[personaId] == true))
        }
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

private fun ActivityItem.toEntity() = ActivityEntity(
    id = id,
    personaId = personaId,
    boldPart = boldPart,
    rest = rest,
    at = createdAt,
    kind = kind.name,
)

private fun ActivityEntity.toModel() = ActivityItem(
    id = id,
    personaId = personaId,
    boldPart = boldPart,
    rest = rest,
    createdAt = at,
    kind = runCatching { ActivityKind.valueOf(kind) }.getOrDefault(ActivityKind.System),
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

    fun activities(base: Long): List<ActivityItem> = listOf(
        act("a1", "sofia", "Sofia Marchetti", " started following you", 8, ActivityKind.Follow, base),
        act("a2", "sofia", "Sofia Marchetti", " mentioned you in a post", 10, ActivityKind.Mention, base),
        act("a3", "marcus", "Marcus Bell", " started following you", 45, ActivityKind.Follow, base),
        act("a4", "elena", "Elena Ruiz", " started following you", 60, ActivityKind.Follow, base),
        act("a5", "nina", "Nina Petrova", " started following you", 120, ActivityKind.Follow, base),
        act("a6", "kai", "Kai Tanaka", " started following you. that basically never happens", 180, ActivityKind.Follow, base),
        act("a7", "you", "Welcome to Only Human", " — fifteen people just got a new neighbor.", 480, ActivityKind.System, base),
    )

    private fun act(
        id: String,
        persona: String,
        bold: String,
        rest: String,
        minutesAgo: Int,
        kind: ActivityKind,
        base: Long,
    ): ActivityItem = ActivityItem(
        id = id,
        personaId = persona,
        boldPart = bold,
        rest = rest,
        createdAt = base - minutesAgo * 60_000L,
        kind = kind,
    )
}
