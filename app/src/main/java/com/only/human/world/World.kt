package com.only.human.world

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.only.human.data.ActivityEntity
import com.only.human.data.DmEntity
import com.only.human.data.FollowEntity
import com.only.human.data.OhDao
import com.only.human.data.OhDatabase
import com.only.human.data.PersonaEntity
import com.only.human.data.PostEntity
import com.only.human.data.ReplyEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * The living state of the network.
 *
 * The UI reads from here; every write goes through here into Room. Coming
 * back after a gap runs the catch-up simulation: the fifteen post, reply,
 * follow and DM while you're gone, and the feed and Activity tab show what
 * you missed — every word written live by the engine, never canned. If a
 * write fails, that moment quietly doesn't happen.
 */
object World {

    val posts = mutableStateListOf<Post>()
    val activities = mutableStateListOf<ActivityItem>()
    val replies = mutableStateListOf<Reply>()
    val dms = mutableStateListOf<Dm>()

    /** personaId -> you follow them. */
    val following = mutableStateMapOf<String, Boolean>()

    /** personaId -> they follow you. */
    val followedBy = mutableStateMapOf<String, Boolean>()

    /** personaId -> they're typing a DM right now. */
    val typing = mutableStateMapOf<String, Boolean>()

    /** personaId -> the last time you opened their thread. */
    val dmSeen = mutableStateMapOf<String, Long>()

    /** The DM thread on screen right now, if any — arrivals there read on the spot. */
    var openDm by mutableStateOf<String?>(null)

    /** The post page on screen right now, if any — replies there skip the Activity row. */
    var openPost by mutableStateOf<String?>(null)

    /** Who the human is. Set once during onboarding, remembered forever. */
    var me by mutableStateOf(Me("You", "you", 0))
        private set

    /** False until onboarding finishes — the shell shows the setup flow. */
    var onboarded by mutableStateOf(false)
        private set

    /** The last time you looked at the Activity tab. Drives the tab badge. */
    var activitySeenAt by mutableStateOf(0L)

    /** Local notifications while you're away — off until you switch them on. */
    var notifyEnabled by mutableStateOf(false)
        private set

    /** You, as the rest of the app sees you. */
    val mePersona: Persona
        get() = Persona(
            id = "you",
            name = me.name,
            handle = me.handle,
            bio = "the only human here.",
            voice = "the human",
            gradient = Cast.avatarPalettes[me.avatarIndex.coerceIn(0, Cast.avatarPalettes.size - 1)],
            joinedYear = "2026",
            followerBoost = 0,
        )

    private var database: OhDatabase? = null
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null
    private var appVisible = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var postSeq = 0

    private const val PREFS = "only_human"
    private const val KEY_LAST_VISIT = "lastVisitAt"
    private const val KEY_ME_NAME = "meName"
    private const val KEY_ME_HANDLE = "meHandle"
    private const val KEY_ME_AVATAR = "meAvatar"
    private const val KEY_ACTIVITY_SEEN = "activitySeenAt"
    private const val KEY_NOTIFY = "notifyMe"

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
     * Show the world immediately, then reconcile with disk: everything shown
     * is what actually happened — restored from Room, with any time you were
     * away simulated for real. Before onboarding, nothing exists yet: the
     * world gets written around your name.
     */
    fun start(context: Context) {
        if (database != null) return
        val db = OhDatabase.get(context)
        database = db
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        Engine.load(p)

        val storedMe = Me(
            name = p.getString(KEY_ME_NAME, "") ?: "",
            handle = p.getString(KEY_ME_HANDLE, "") ?: "",
            avatarIndex = p.getInt(KEY_ME_AVATAR, 0),
        )
        if (storedMe.name.isNotEmpty() && storedMe.handle.isNotEmpty()) {
            me = storedMe
            onboarded = true
        }
        activitySeenAt = p.getLong(KEY_ACTIVITY_SEEN, 0L)
        notifyEnabled = p.getBoolean(KEY_NOTIFY, false)
        appContext = context.applicationContext

        scope.launch {
            val dao = db.dao()
            val firstRun = dao.postCount() == 0
            if (firstRun && onboarded) {
                dao.upsertPosts(posts.map { it.toEntity() })
                dao.upsertActivities(activities.map { it.toEntity() })
                dao.upsertReplies(replies.map { it.toEntity() })
                following.forEach { (id, value) ->
                    dao.upsertFollow(FollowEntity(id, value, followedBy[id] == true))
                }
            }

            val lastVisit = prefs?.getLong(KEY_LAST_VISIT, 0L) ?: 0L
            val now = System.currentTimeMillis()
            // The cast you built comes back first, so they take part in the
            // catch-up like everyone else; their seen-markers come back too.
            val storedPersonas = dao.personas().map { it.toModel() }
            withContext(Dispatchers.Main) {
                Cast.setCustom(storedPersonas)
                Cast.all.forEach { dmSeen[it.id] = p.getLong("dmSeen_${it.id}", 0L) }
            }
            if (!firstRun && lastVisit in 1 until now) {
                runCatchUp(dao, lastVisit, now)
            }

            val storedPosts = dao.posts().map { it.toModel() }
            val storedActivities = dao.activities().map { it.toModel() }
            val storedReplies = dao.replies().map { it.toModel() }
            val storedDms = dao.dms().map { it.toModel() }
            val storedFollows = dao.follows()
            withContext(Dispatchers.Main) {
                // If onboarding completed while this boot was still running,
                // the freshly written world in memory is the truth — keep it.
                if (storedPosts.isNotEmpty() || !onboarded) {
                    posts.clear()
                    posts.addAll(storedPosts)
                    activities.clear()
                    activities.addAll(storedActivities)
                    replies.clear()
                    replies.addAll(storedReplies)
                }
                dms.clear()
                dms.addAll(storedDms)
                storedFollows.forEach {
                    following[it.personaId] = it.following
                    followedBy[it.personaId] = it.followsYou
                }
            }
            prefs?.edit()?.putLong(KEY_LAST_VISIT, System.currentTimeMillis())?.apply()
        }
    }

    /**
     * Onboarding finished: remember who you are, note the welcome, and let
     * Sofia do what Sofia does.
     */
    fun completeOnboarding(name: String, handle: String, avatarIndex: Int) {
        val cleanName = name.trim().take(24)
        val cleanHandle = handle.trim().lowercase().take(16)
        if (cleanName.isEmpty() || cleanHandle.isEmpty()) return

        me = Me(cleanName, cleanHandle, avatarIndex)
        onboarded = true
        activitySeenAt = System.currentTimeMillis()
        prefs?.edit()
            ?.putString(KEY_ME_NAME, cleanName)
            ?.putString(KEY_ME_HANDLE, cleanHandle)
            ?.putInt(KEY_ME_AVATAR, avatarIndex)
            ?.putLong(KEY_ACTIVITY_SEEN, activitySeenAt)
            ?.apply()

        // The world starts empty and fills up for real: a system welcome
        // note, then the fifteen wake up one by one and write.
        val base = System.currentTimeMillis()
        activities.add(
            ActivityItem(
                id = "welcome-$base",
                personaId = "you",
                boldPart = "Welcome to Only Human",
                rest = " — fifteen people just got a new neighbor.",
                createdAt = base - 10 * 60_000L,
                kind = ActivityKind.System,
            ),
        )

        scope.launch {
            val dao = database?.dao() ?: return@launch
            dao.upsertActivities(activities.map { it.toEntity() })
            following.forEach { (id, value) ->
                dao.upsertFollow(FollowEntity(id, value, followedBy[id] == true))
            }
            prefs?.edit()?.putLong(KEY_LAST_VISIT, System.currentTimeMillis())?.apply()
        }
        // Sofia can't help herself — the first DM arrives shortly after
        // you move in. And the rest of the neighborhood is waking up.
        welcomeDm("sofia")
        warmUpWorld()
    }

    /**
     * Someone new joins the neighborhood: saved to Room, follows you right
     * away, and their first post is written for real seconds later.
     * Returns the new persona's id, or null when the name or handle can't work.
     */
    fun createPersona(
        name: String,
        handle: String,
        occupation: String,
        location: String,
        bio: String,
        voice: String,
        gradientIndex: Int,
    ): String? {
        val cleanName = name.trim().take(24)
        val cleanHandle = handle.trim().lowercase().removePrefix("@")
            .filter { it.isLetterOrDigit() || it == '_' }.take(16)
        if (cleanName.isEmpty() || cleanHandle.length < 3) return null
        if (cleanHandle == me.handle) return null
        if (Cast.people.any { it.handle.equals(cleanHandle, ignoreCase = true) }) return null

        val id = "ai-$cleanHandle"
        val persona = Persona(
            id = id,
            name = cleanName,
            handle = cleanHandle,
            bio = bio.trim().take(150).ifEmpty { "new in the neighborhood." },
            occupation = occupation.trim().take(60),
            location = location.trim().take(40),
            voice = voice.trim().take(220).ifEmpty { "warm, casual, writes short lowercase lines" },
            gradient = Cast.avatarPalettes[gradientIndex.coerceIn(0, Cast.avatarPalettes.size - 1)],
            joinedYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString(),
            followerBoost = 0,
        )
        Cast.addCustom(persona)
        following[id] = false
        followedBy[id] = true

        val item = ActivityItem(
            id = "joined-$id-${System.currentTimeMillis()}",
            personaId = id,
            boldPart = cleanName,
            rest = " just moved in and followed you",
            createdAt = System.currentTimeMillis(),
            kind = ActivityKind.Follow,
        )
        activities.add(0, item)
        persistActivity(item)

        scope.launch {
            val dao = database?.dao() ?: return@launch
            dao.upsertPersona(persona.toEntity())
            dao.upsertFollow(FollowEntity(id, false, true))
        }

        // They say hello to the feed in their own voice — written live.
        scope.launch {
            delay((2_000L..5_000L).random())
            val text = writePostText(persona) ?: return@launch
            val post = Post(
                id = "intro-$id-${System.currentTimeMillis()}",
                authorId = id,
                text = text,
                createdAt = System.currentTimeMillis(),
                likes = 0,
                replies = 0,
            )
            persistPost(post)
            withContext(Dispatchers.Main) { insertPost(post) }
        }
        return id
    }

    /**
     * Called when the app goes to the background. The next launch simulates
     * the gap between this moment and then.
     */
    fun markAway() {
        prefs?.edit()?.putLong(KEY_LAST_VISIT, System.currentTimeMillis())?.apply()
    }

    /** True while the app is on screen — notifications only ping when it isn't. */
    fun setVisible(visible: Boolean) {
        appVisible = visible
    }

    fun enableNotifications(enabled: Boolean) {
        if (notifyEnabled == enabled) return
        notifyEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_NOTIFY, enabled)?.apply()
    }

    /** One quiet line in the shade — only if they're welcome and you're gone. */
    private fun tell(title: String, body: String, tag: String) {
        if (!notifyEnabled || appVisible) return
        val ctx = appContext ?: return
        Notifier.post(ctx, tag, title, body.take(180))
    }

    /**
     * The time you were away: metrics first (likes on existing text,
     * follows), then every word that needs writing is actually written —
     * one live call at a time, arriving as it's ready.
     */
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
        if (result.edited.isNotEmpty()) dao.upsertPosts(result.edited.map { it.toEntity() })
        if (result.activities.isNotEmpty()) dao.upsertActivities(result.activities.map { it.toEntity() })
        result.followsYou.forEach { (id, value) ->
            val mine = followRows.firstOrNull { it.personaId == id }?.following ?: (following[id] == true)
            dao.upsertFollow(FollowEntity(id, mine, value))
        }
        writeAway(result)
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

    // ---- replies ---------------------------------------------------------

    fun repliesFor(postId: String): List<Reply> =
        replies.filter { it.postId == postId }.sortedBy { it.createdAt }

    fun addReply(postId: String, text: String) {
        val reply = Reply(
            id = "local-r-${System.currentTimeMillis()}-${postSeq++}",
            postId = postId,
            authorId = "you",
            text = text,
            createdAt = System.currentTimeMillis(),
        )
        replies += reply
        bumpReplyCount(postId)
        persistReply(reply)
        schedulePersonaReply(postId)
    }

    private fun bumpReplyCount(postId: String) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index < 0) return
        val updated = posts[index].copy(replies = posts[index].replies + 1)
        posts[index] = updated
        persistPost(updated)
    }

    /**
     * The author writes back shortly after — like a person, not a webhook.
     * Skipped now and then so it never feels automatic, and only when the
     * engine can actually write it — a failed write means no reply at all.
     */
    private fun schedulePersonaReply(postId: String) {
        val post = posts.firstOrNull { it.id == postId } ?: return
        if (post.authorId == "you") return
        if (Random.nextDouble() > 0.65) return
        if (post.authorId == "kai" && Random.nextDouble() < 0.6) return
        val authorId = post.authorId
        scope.launch {
            delay((9_000L..28_000L).random())
            val persona = Cast.byId(authorId)
            val myReply = replies.lastOrNull { it.postId == postId && it.authorId == "you" }?.text
            val text = engineReplyText(persona, post, myReply) ?: return@launch
            val reply = Reply(
                id = "voice-r-${System.currentTimeMillis()}-${authorId}",
                postId = postId,
                authorId = authorId,
                text = text,
                createdAt = System.currentTimeMillis(),
            )
            val item = ActivityItem(
                id = reply.id,
                personaId = authorId,
                boldPart = persona.name,
                rest = " replied: \u201C${reply.text}\u201D",
                createdAt = reply.createdAt,
                kind = ActivityKind.Reply,
            )
            persistReply(reply)
            withContext(Dispatchers.Main) {
                replies += reply
                bumpReplyCount(postId)
                // Reading the post right now? The reply shows up where you're looking.
                if (openPost != postId) {
                    persistActivity(item)
                    activities.add(0, item)
                }
            }
            tell(persona.name, text, "reply-$authorId")
        }
    }

    private suspend fun engineReplyText(persona: Persona, post: Post, myReply: String?): String? {
        if (!Engine.config.ready) return null
        val prompt = buildString {
            append("Context — your post: \u201C${post.text}\u201D\n")
            if (myReply != null) {
                append("${me.name} (@${me.handle}) replied to it: \u201C$myReply\u201D\n")
                append("Write your reply back. One or two short sentences, plain text only, no quotes.")
            } else {
                append("${me.name} (@${me.handle}) just liked it.\n")
                append("Write a short follow-up comment on your own post. One or two sentences, plain text only, no quotes.")
            }
        }
        return Engine.chat(Engine.personaPrompt(persona), listOf("user" to prompt), maxTokens = 140)
    }

    // ---- direct messages ---------------------------------------------------

    /** The thread with [personaId], oldest first. */
    fun dmWith(personaId: String): List<Dm> =
        dms.filter { it.personaId == personaId }.sortedBy { it.at }

    /** Every conversation, newest first — what the Messages list shows. */
    fun dmThreads(): List<DmThread> =
        dms.groupBy { it.personaId }
            .mapNotNull { (id, list) ->
                list.maxByOrNull { it.at }?.let { DmThread(id, it, unreadDmCount(id)) }
            }
            .sortedByDescending { it.last.at }

    /** You send a DM; they write back shortly after — in voice, like a person. */
    fun sendDm(personaId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        val dm = Dm(
            id = "local-dm-$now-${postSeq++}",
            personaId = personaId,
            fromMe = true,
            text = clean.take(600),
            at = now,
        )
        dms += dm
        persistDm(dm)
        markDmSeen(personaId)
        scheduleDmReply(personaId)
    }

    private fun scheduleDmReply(personaId: String) {
        val persona = Cast.byId(personaId)
        scope.launch {
            withContext(Dispatchers.Main) { typing[personaId] = true }
            delay((12_000L..30_000L).random())
            val history = dms.filter { it.personaId == personaId }.takeLast(12)
                .map { (if (it.fromMe) "user" else "assistant") to it.text }
            val text = engineDmReply(persona, history)
            if (text == null) {
                withContext(Dispatchers.Main) { typing[personaId] = false }
                return@launch
            }
            deliverDm(personaId, text)
        }
    }

    /** They notice you exist and write first — the welcome message. */
    private fun welcomeDm(personaId: String) {
        val persona = Cast.byId(personaId)
        scope.launch {
            delay((14_000L..26_000L).random())
            withContext(Dispatchers.Main) { typing[personaId] = true }
            val text = engineDmOpen(persona)
            if (text == null) {
                withContext(Dispatchers.Main) { typing[personaId] = false }
                return@launch
            }
            deliverDm(personaId, text)
        }
    }

    /** Delivers an incoming DM plus its Activity row. Safe from any thread. */
    private suspend fun deliverDm(personaId: String, text: String, at: Long = System.currentTimeMillis()) {
        val now = at
        val persona = Cast.byId(personaId)
        val dm = Dm(
            id = "voice-dm-$now-$personaId",
            personaId = personaId,
            fromMe = false,
            text = text,
            at = now,
        )
        val item = ActivityItem(
            id = "dm-a-$now-$personaId",
            personaId = personaId,
            boldPart = persona.name,
            rest = " sent you a message",
            createdAt = now,
            kind = ActivityKind.Message,
        )
        val insideThread = openDm == personaId
        persistDm(dm)
        withContext(Dispatchers.Main) {
            typing[personaId] = false
            dms += dm
            if (insideThread) {
                // You're in the thread — it's read the moment it lands.
                markDmSeen(personaId)
            } else {
                persistActivity(item)
                activities.add(0, item)
            }
        }
        tell(persona.name, text, "dm-$personaId")
    }

    private suspend fun engineDmReply(persona: Persona, history: List<Pair<String, String>>): String? {
        if (!Engine.config.ready) return null
        if (history.isEmpty() || history.last().first != "user") return null
        return Engine.chat(Engine.dmPrompt(persona), history, maxTokens = 130)
    }

    private suspend fun engineDmOpen(persona: Persona): String? {
        if (!Engine.config.ready) return null
        return Engine.chat(
            Engine.dmPrompt(persona),
            listOf(
                "user" to "Write the first DM you'd send to your new neighbor ${me.name} — something personal, " +
                    "in your voice, that gives them an easy reason to reply. One or two lines.",
            ),
            maxTokens = 120,
        )
    }

    // ---- writing, for real -------------------------------------------------

    /** First run: the fifteen wake up and post, one by one, for real. */
    private fun warmUpWorld() {
        scope.launch {
            if (!Engine.config.ready) return@launch
            val base = System.currentTimeMillis()
            var minutesAgo = (3..20).random()
            for (persona in Cast.people.shuffled().take(8)) {
                delay((1_500L..6_000L).random())
                val text = writePostText(persona) ?: continue
                val post = Post(
                    id = "warm-${base}-${persona.id}",
                    authorId = persona.id,
                    text = text,
                    createdAt = base - minutesAgo * 60_000L,
                    likes = 0,
                    replies = 0,
                )
                minutesAgo += (9..85).random()
                persistPost(post)
                withContext(Dispatchers.Main) { insertPost(post) }
            }
        }
    }

    /** The while-you-were-away items that carry text, written live and staggered. */
    private fun writeAway(result: Simulation.Result) {
        result.posts.forEach { intent ->
            scope.launch {
                delay((2_000L..16_000L).random())
                val persona = Cast.byId(intent.personaId)
                val text = writePostText(persona) ?: return@launch
                val post = Post(
                    id = "away-${intent.at}-${persona.id}",
                    authorId = persona.id,
                    text = text,
                    createdAt = intent.at,
                    likes = 0,
                    replies = 0,
                )
                persistPost(post)
                withContext(Dispatchers.Main) { insertPost(post) }
            }
        }

        result.replies.forEach { intent ->
            scope.launch {
                delay((3_000L..20_000L).random())
                val persona = Cast.byId(intent.personaId)
                val target = posts.firstOrNull { it.id == intent.postId } ?: return@launch
                val text = Engine.chat(
                    Engine.personaPrompt(persona),
                    listOf(
                        "user" to "Your friend ${me.name} (@${me.handle}) posted: \u201C${target.text}\u201D\n" +
                            "Write a short reply to it, in your voice. One or two sentences, plain text only.",
                    ),
                    maxTokens = 140,
                ) ?: return@launch
                val reply = Reply(
                    id = "away-r-${intent.at}-${persona.id}",
                    postId = intent.postId,
                    authorId = persona.id,
                    text = text,
                    createdAt = intent.at,
                )
                val item = ActivityItem(
                    id = reply.id,
                    personaId = persona.id,
                    boldPart = persona.name,
                    rest = " replied: \u201C$text\u201D",
                    createdAt = intent.at,
                    kind = ActivityKind.Reply,
                )
                persistReply(reply)
                withContext(Dispatchers.Main) {
                    replies += reply
                    bumpReplyCount(intent.postId)
                    if (openPost != intent.postId) {
                        persistActivity(item)
                        activities.add(0, item)
                    }
                }
            }
        }

        result.mention?.let { intent ->
            scope.launch {
                delay((4_000L..24_000L).random())
                val persona = Cast.byId(intent.personaId)
                val text = Engine.chat(
                    Engine.personaPrompt(persona),
                    listOf(
                        "user" to "Something in your day made you think of your neighbor ${me.name} (@${me.handle}). " +
                            "Write a short post mentioning them by @handle. One or two sentences, plain text only.",
                    ),
                    maxTokens = 140,
                ) ?: return@launch
                val post = Post(
                    id = "away-m-${intent.at}-${persona.id}",
                    authorId = persona.id,
                    text = text,
                    createdAt = intent.at,
                    likes = 0,
                    replies = 0,
                )
                val item = ActivityItem(
                    id = "away-m-a-${intent.at}-${persona.id}",
                    personaId = persona.id,
                    boldPart = persona.name,
                    rest = " mentioned you in a post",
                    createdAt = intent.at,
                    kind = ActivityKind.Mention,
                )
                persistPost(post)
                withContext(Dispatchers.Main) {
                    insertPost(post)
                    persistActivity(item)
                    activities.add(0, item)
                }
            }
        }

        result.dms.forEach { intent ->
            scope.launch {
                delay((4_000L..22_000L).random())
                val persona = Cast.byId(intent.personaId)
                val text = Engine.chat(
                    Engine.dmPrompt(persona),
                    listOf(
                        "user" to "Send ${me.name} a short message about your day — a thought, a question, " +
                            "something only you would send. One or two lines, plain text only.",
                    ),
                    maxTokens = 130,
                ) ?: return@launch
                deliverDm(persona.id, text, at = intent.at)
            }
        }
    }

    /** A post written in their voice — the one way post text comes to exist. */
    private suspend fun writePostText(persona: Persona): String? =
        Engine.chat(
            Engine.personaPrompt(persona),
            listOf(
                "user" to "Write your latest post for the feed — something small from your day, " +
                    "in your voice. One or two short sentences, plain text only, no hashtags.",
            ),
            maxTokens = 140,
        )

    /** Keeps the feed newest-first while posts land out of order. */
    private fun insertPost(post: Post) {
        val index = posts.indexOfFirst { it.createdAt < post.createdAt }
        if (index < 0) posts.add(post) else posts.add(index, post)
    }

    // ---- seen state + badges -----------------------------------------------

    fun markActivitySeen() {
        val now = System.currentTimeMillis()
        activitySeenAt = now
        prefs?.edit()?.putLong(KEY_ACTIVITY_SEEN, now)?.apply()
    }

    /** Activity rows you haven't looked at; the system welcome doesn't count. */
    fun unreadActivityCount(): Int =
        activities.count { it.createdAt > activitySeenAt && it.kind != ActivityKind.System }

    fun markDmSeen(personaId: String) {
        val now = System.currentTimeMillis()
        dmSeen[personaId] = now
        prefs?.edit()?.putLong("dmSeen_$personaId", now)?.apply()
    }

    fun unreadDmCount(personaId: String): Int {
        val seen = dmSeen[personaId] ?: 0L
        return dms.count { it.personaId == personaId && !it.fromMe && it.at > seen }
    }

    fun unreadDmsTotal(): Int = Cast.people.sumOf { unreadDmCount(it.id) }

    // ---- everything else ---------------------------------------------------

    private fun persistReply(reply: Reply) {
        val db = database ?: return
        scope.launch { db.dao().upsertReply(reply.toEntity()) }
    }

    private fun persistActivity(item: ActivityItem) {
        val db = database ?: return
        scope.launch { db.dao().upsertActivity(item.toEntity()) }
    }

    private fun persistDm(dm: Dm) {
        val db = database ?: return
        scope.launch { db.dao().upsertDm(dm.toEntity()) }
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

private fun Reply.toEntity() = ReplyEntity(
    id = id,
    postId = postId,
    authorId = authorId,
    text = text,
    createdAt = createdAt,
)

private fun ReplyEntity.toModel() = Reply(
    id = id,
    postId = postId,
    authorId = authorId,
    text = text,
    createdAt = createdAt,
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

private fun Dm.toEntity() = DmEntity(
    id = id,
    personaId = personaId,
    fromMe = fromMe,
    text = text,
    at = at,
)

private fun DmEntity.toModel() = Dm(
    id = id,
    personaId = personaId,
    fromMe = fromMe,
    text = text,
    at = at,
)

private fun Persona.toEntity() = PersonaEntity(
    id = id,
    name = name,
    handle = handle,
    occupation = occupation,
    location = location,
    bio = bio,
    voice = voice,
    gradient = gradient.take(2).joinToString(",") { it.toArgb().toString() },
    joinedYear = joinedYear,
    followerBoost = followerBoost,
    createdAt = System.currentTimeMillis(),
)

private fun PersonaEntity.toModel() = Persona(
    id = id,
    name = name,
    handle = handle,
    bio = bio,
    occupation = occupation,
    location = location,
    voice = voice,
    gradient = decodeGradient(gradient),
    joinedYear = joinedYear,
    followerBoost = followerBoost,
)

/** Two ARGB ints, comma-separated — how gradients survive Room. */
private fun decodeGradient(raw: String): List<Color> {
    val parsed = raw.split(",").mapNotNull { it.trim().toLongOrNull()?.toInt() }
    return when {
        parsed.size >= 2 -> listOf(Color(parsed[0]), Color(parsed[1]))
        parsed.size == 1 -> listOf(Color(parsed[0]), Color(parsed[0]))
        else -> Cast.avatarPalettes.first()
    }
}
