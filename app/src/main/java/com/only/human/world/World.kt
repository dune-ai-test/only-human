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
import com.only.human.data.GroupEntity
import com.only.human.data.GroupMsgEntity
import com.only.human.data.OhDao
import com.only.human.data.OhDatabase
import com.only.human.data.PersonaEntity
import com.only.human.data.PostEntity
import com.only.human.data.ReplyEntity
import com.only.human.data.StoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
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
    val groups = mutableStateListOf<Group>()
    val groupMsgs = mutableStateListOf<GroupMsg>()

    /** People waiting under your posts for a yes — nothing written yet. */
    val commentIntents = mutableStateListOf<CommentIntent>()

    /** Stories — everyone's right-now lines. Each one lives for a day. */
    val stories = mutableStateListOf<Story>()

    /** The ones still alive — under a day old — unseen first, then newest. */
    fun freshStories(): List<Story> {
        val now = System.currentTimeMillis()
        return stories
            .filter { now - it.createdAt < 24 * 60 * 60 * 1000L }
            .sortedWith(compareBy({ it.seen }, { -it.createdAt }))
    }

    /** Marked the moment it's watched — stays seen for its whole life. */
    fun markStorySeen(id: String) {
        val index = stories.indexOfFirst { it.id == id }
        if (index < 0 || stories[index].seen) return
        stories[index] = stories[index].copy(seen = true)
        scope.launch { database?.dao()?.markStorySeen(id) }
    }

    /** personaId -> the model they write through. Missing means the engine default. */
    val personaModels = mutableStateMapOf<String, String>()

    fun personaModel(personaId: String): String? = personaModels[personaId]

    /** Pick the model one person writes through — null or blank is the default. */
    fun setPersonaModel(personaId: String, model: String?) {
        applyPersonaModel(personaId, model)
        if (Server.active) {
            Server.act("set_model", JSONObject().put("personaId", personaId).put("model", model?.trim().orEmpty()))
        }
    }

    /** The model map, saved — the world's copy and this phone's stay in step. */
    private fun applyPersonaModel(personaId: String, model: String?) {
        val clean = model?.trim().orEmpty()
        if (clean.isEmpty()) personaModels.remove(personaId) else personaModels[personaId] = clean
        val json = JSONObject()
        personaModels.forEach { (id, m) -> json.put(id, m) }
        prefs?.edit()?.putString(KEY_PERSONA_MODELS, json.toString())?.apply()
    }

    /** personaId -> you follow them. */
    val following = mutableStateMapOf<String, Boolean>()

    /** personaId -> they follow you. */
    val followedBy = mutableStateMapOf<String, Boolean>()

    /** personaId -> they're typing a DM right now. */
    val typing = mutableStateMapOf<String, Boolean>()

    /** groupId -> someone's typing in the group right now. */
    val groupTyping = mutableStateMapOf<String, Boolean>()

    /** personaId -> the last time you opened their thread. */
    val dmSeen = mutableStateMapOf<String, Long>()

    /** groupId -> the last time you opened the room. */
    val groupSeen = mutableStateMapOf<String, Long>()

    /** The DM thread on screen right now, if any — arrivals there read on the spot. */
    var openDm by mutableStateOf<String?>(null)

    /** The group chat on screen right now, if any — arrivals there read on the spot. */
    var openGroup by mutableStateOf<String?>(null)

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
    private const val KEY_PERSONA_MODELS = "personaModels"
    private const val KEY_COMMENT_INTENTS = "commentIntents"

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
        Server.load(p)
        p.getString(KEY_PERSONA_MODELS, null)?.let { raw ->
            try {
                val json = JSONObject(raw)
                json.keys().forEach { id -> personaModels[id] = json.getString(id) }
            } catch (_: Exception) {
                // A leftover blob from another life: everyone writes default.
            }
        }
        p.getString(KEY_COMMENT_INTENTS, null)?.let { raw ->
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    commentIntents += CommentIntent(
                        id = o.getString("id"),
                        postId = o.getString("postId"),
                        personaId = o.getString("personaId"),
                        at = o.getLong("at"),
                    )
                }
            } catch (_: Exception) {
                // Unreadable queue — nobody was waiting.
            }
        }

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
            // A server world keeps its own time — the cache catches up by
            // sync, never by simulating what the neighbors did while away.
            if (!Server.active && !firstRun && lastVisit in 1 until now) {
                runCatchUp(dao, lastVisit, now)
            }

            val storedPosts = dao.posts().map { it.toModel() }
            val storedActivities = dao.activities().map { it.toModel() }
            val storedReplies = dao.replies().map { it.toModel() }
            val storedDms = dao.dms().map { it.toModel() }
            val storedGroups = dao.groups().map { it.toModel() }
            val storedGroupMsgs = dao.groupMsgs().map { it.toModel() }
            val storedStories = dao.stories().map { it.toModel() }
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
                groups.clear()
                groups.addAll(storedGroups)
                groupMsgs.clear()
                groupMsgs.addAll(storedGroupMsgs)
                storedGroups.forEach { groupSeen[it.id] = p.getLong("gseen_${it.id}", 0L) }
                if (storedStories.isNotEmpty()) {
                    stories.clear()
                    stories.addAll(storedStories)
                }
                storedFollows.forEach {
                    following[it.personaId] = it.following
                    followedBy[it.personaId] = it.followsYou
                }
            }
            prefs?.edit()?.putLong(KEY_LAST_VISIT, System.currentTimeMillis())?.apply()
            if (Server.active) {
                // The cache is on screen — now go find out what really happened.
                Server.onBoot()
            } else {
                refreshStories()
            }
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
        // (A server world does its own warm-up — this phone only watches.)
        if (!Server.active) {
            welcomeDm("sofia")
            warmUpWorld()
            refreshStories()
        }
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

        scope.launch {
            val dao = database?.dao() ?: return@launch
            dao.upsertPersona(persona.toEntity())
            dao.upsertFollow(FollowEntity(id, false, true))
        }

        if (Server.active) {
            // The world writes them in and says hello — the join shows up
            // through sync, with an id everyone shares.
            Server.act("create_persona", Server.personaPayload(persona))
            return id
        }

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
        // On a server world the id is made here, so your post shows up the
        // instant you hit send — the world adopts this same id on arrival.
        val post = Post(
            id = if (Server.active) "s-post-${UUID.randomUUID()}" else "local-${System.currentTimeMillis()}-${postSeq++}",
            authorId = authorId,
            text = text,
            createdAt = System.currentTimeMillis(),
            likes = 0,
            replies = 0,
        )
        posts.add(0, post)
        persistPost(post)
        if (authorId == "you") {
            if (Server.active) {
                Server.act("post", JSONObject().put("id", post.id).put("text", post.text))
            } else {
                scheduleComments(post)
            }
        }
    }

    // ---- replies ---------------------------------------------------------

    fun repliesFor(postId: String): List<Reply> =
        replies.filter { it.postId == postId }.sortedBy { it.createdAt }

    fun addReply(postId: String, text: String) {
        val reply = Reply(
            id = if (Server.active) "s-reply-${UUID.randomUUID()}" else "local-r-${System.currentTimeMillis()}-${postSeq++}",
            postId = postId,
            authorId = "you",
            text = text,
            createdAt = System.currentTimeMillis(),
        )
        replies += reply
        bumpReplyCount(postId)
        persistReply(reply)
        if (Server.active) {
            Server.act("reply", JSONObject().put("id", reply.id).put("postId", postId).put("text", text))
        } else {
            schedulePersonaReply(postId)
            scheduleCommenterReply(postId)
        }
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
        return Engine.chat(
            Engine.personaPrompt(persona),
            listOf("user" to prompt),
            maxTokens = 140,
            model = personaModel(persona.id),
        )
    }

    // ---- direct messages ---------------------------------------------------

    /**
     * Your post doesn't land in a void: over the next minutes the neighbors
     * wander by and ask to comment. Nothing is written yet — each one waits
     * under the post for your say-so, and only then does the engine run.
     */
    private fun scheduleComments(post: Post) {
        if (!Engine.config.ready) return
        val followers = Cast.all.filter { it.id != "you" && followedBy[it.id] == true }.shuffled()
        val others = Cast.all.filter { it.id != "you" && followedBy[it.id] != true }.shuffled()
        val commenters = (followers.take(3) + others.take(1)).distinct()
        if (commenters.isEmpty()) return
        scope.launch {
            for (persona in commenters) {
                delay((10_000L..30_000L).random())
                withContext(Dispatchers.Main) {
                    commentIntents += CommentIntent(
                        id = "ask-${post.id}-${persona.id}",
                        postId = post.id,
                        personaId = persona.id,
                        at = System.currentTimeMillis(),
                    )
                    persistCommentIntents()
                }
            }
        }
    }

    /** You said yes — now they write it, for real, through the queue. */
    fun postComment(intentId: String) {
        val intent = commentIntents.firstOrNull { it.id == intentId } ?: return
        if (intent.writing) return
        if (Server.active) {
            // The world writes it; the ask's state comes back through sync —
            // approved (writing), pending again (couldn't write it), posted.
            setIntentState(intentId, writing = true, failed = false)
            Server.act(
                "approve",
                JSONObject().put("askId", intentId),
                queueOnFailure = false,
                onRejected = { setIntentState(intentId, writing = false, failed = true) },
            )
            Server.kickIn(30_000)
            return
        }
        setIntentState(intentId, writing = true, failed = false)
        scope.launch {
            val post = posts.firstOrNull { it.id == intent.postId }
            val persona = Cast.byId(intent.personaId)
            val text = if (post != null) engineCommentText(persona, post) else null
            if (text == null) {
                withContext(Dispatchers.Main) { setIntentState(intentId, writing = false, failed = true) }
                return@launch
            }
            val reply = Reply(
                id = "voice-c-${System.currentTimeMillis()}-${persona.id}",
                postId = intent.postId,
                authorId = persona.id,
                text = text,
                createdAt = System.currentTimeMillis(),
            )
            val item = ActivityItem(
                id = reply.id,
                personaId = persona.id,
                boldPart = persona.name,
                rest = " commented: \u201C${reply.text}\u201D",
                createdAt = reply.createdAt,
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
                commentIntents.removeAll { it.id == intentId }
                persistCommentIntents()
            }
            tell(persona.name, text, "reply-${persona.id}")
        }
    }

    /** Not now — they were never writing, so nothing happened. */
    fun skipComment(intentId: String) {
        commentIntents.removeAll { it.id == intentId }
        persistCommentIntents()
        if (Server.active) Server.act("skip", JSONObject().put("askId", intentId))
    }

    private fun setIntentState(intentId: String, writing: Boolean, failed: Boolean) {
        val index = commentIntents.indexOfFirst { it.id == intentId }
        if (index < 0) return
        commentIntents[index] = commentIntents[index].copy(writing = writing, failed = failed)
    }

    private fun persistCommentIntents() {
        val arr = JSONArray()
        commentIntents.forEach { intent ->
            arr.put(
                JSONObject().apply {
                    put("id", intent.id)
                    put("postId", intent.postId)
                    put("personaId", intent.personaId)
                    put("at", intent.at)
                },
            )
        }
        prefs?.edit()?.putString(KEY_COMMENT_INTENTS, arr.toString())?.apply()
    }

    private suspend fun engineCommentText(persona: Persona, post: Post): String? {
        if (!Engine.config.ready) return null
        val prompt = buildString {
            append("${me.name} (@${me.handle}) posted: \u201C${post.text}\u201D\n")
            append("Write a short comment on their post, in your voice. One or two sentences, plain text only, no quotes.")
        }
        return Engine.chat(
            Engine.personaPrompt(persona),
            listOf("user" to prompt),
            maxTokens = 140,
            model = personaModel(persona.id),
        )
    }

    /**
     * You replied to a comment on your own post — the commenter writes back
     * shortly after, to what you actually said. Sometimes they're busy.
     */
    private fun scheduleCommenterReply(postId: String) {
        val post = posts.firstOrNull { it.id == postId } ?: return
        if (post.authorId != "you") return
        if (!Engine.config.ready) return
        val target = replies.lastOrNull { it.postId == postId && it.authorId != "you" } ?: return
        if (Random.nextDouble() > 0.8) return
        val persona = Cast.byId(target.authorId)
        scope.launch {
            delay((10_000L..30_000L).random())
            val myReply = replies.lastOrNull { it.postId == postId && it.authorId == "you" }?.text
            val prompt = buildString {
                append("Context — ${me.name}'s post: \u201C${post.text}\u201D\n")
                append("You commented: \u201C${target.text}\u201D\n")
                if (myReply != null) {
                    append("${me.name} (@${me.handle}) replied to you: \u201C$myReply\u201D\n")
                }
                append("Write your reply back to them. One or two short sentences, plain text only, no quotes.")
            }
            val text = Engine.chat(
                Engine.personaPrompt(persona),
                listOf("user" to prompt),
                maxTokens = 140,
                model = personaModel(persona.id),
            ) ?: return@launch
            val reply = Reply(
                id = "voice-b-${System.currentTimeMillis()}-${persona.id}",
                postId = postId,
                authorId = persona.id,
                text = text,
                createdAt = System.currentTimeMillis(),
            )
            val item = ActivityItem(
                id = reply.id,
                personaId = persona.id,
                boldPart = persona.name,
                rest = " replied: \u201C${reply.text}\u201D",
                createdAt = reply.createdAt,
                kind = ActivityKind.Reply,
            )
            persistReply(reply)
            withContext(Dispatchers.Main) {
                replies += reply
                bumpReplyCount(postId)
                if (openPost != postId) {
                    persistActivity(item)
                    activities.add(0, item)
                }
            }
            tell(persona.name, text, "reply-${persona.id}")
        }
    }

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
            id = if (Server.active) "s-dm-${UUID.randomUUID()}" else "local-dm-$now-${postSeq++}",
            personaId = personaId,
            fromMe = true,
            text = clean.take(600),
            at = now,
        )
        dms += dm
        persistDm(dm)
        markDmSeen(personaId)
        if (Server.active) {
            // They're thinking over there, at their own pace. Three minutes
            // of dots, then the dots give up — an answer may still land.
            typing[personaId] = true
            val mine = dm
            scope.launch {
                delay(180_000)
                val answered = dms.any { it.personaId == personaId && !it.fromMe && it.at > mine.at }
                if (!answered) withContext(Dispatchers.Main) { typing[personaId] = false }
            }
            Server.act("dm", JSONObject().put("id", dm.id).put("personaId", personaId).put("text", dm.text))
        } else {
            scheduleDmReply(personaId)
        }
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
        return Engine.chat(Engine.dmPrompt(persona), history, maxTokens = 130, model = personaModel(persona.id))
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
            model = personaModel(persona.id),
        )
    }

    // ---- group chats -------------------------------------------------------

    /** The room's messages, oldest first. */
    fun groupMsgsWith(groupId: String): List<GroupMsg> =
        groupMsgs.filter { it.groupId == groupId }.sortedBy { it.at }

    /** Every room, newest first — what the Messages list shows. */
    fun groupThreads(): List<GroupThread> =
        groups.mapNotNull { g ->
            groupMsgs.filter { it.groupId == g.id }.maxByOrNull { it.at }
                ?.let { GroupThread(g, it, unreadGroupCount(g.id)) }
        }.sortedByDescending { it.last.at }

    /** A new room. On a server world the snapshot comes back through sync. */
    fun createGroup(name: String, topic: String, memberIds: List<String>) {
        val cleanName = name.trim().take(60)
        val cleanTopic = topic.trim().take(140)
        val members = memberIds.filter { it.isNotBlank() }.distinct()
        if (cleanName.isEmpty() || members.isEmpty()) return
        if (Server.active) {
            Server.act(
                "group_create",
                JSONObject().put("name", cleanName).put("topic", cleanTopic).put("members", JSONArray(members)),
            )
            Server.kick()
            return
        }
        val now = System.currentTimeMillis()
        val group = Group(
            id = "local-g-$now",
            name = cleanName,
            topic = cleanTopic,
            members = members,
            createdAt = now,
            updatedAt = now,
        )
        groups += group
        persistGroup(group)
    }

    /** Rename or re-topic a room; the world confirms with a fresh snapshot. */
    fun updateGroup(groupId: String, name: String, topic: String) {
        val cleanName = name.trim().take(60)
        val cleanTopic = topic.trim().take(140)
        if (cleanName.isEmpty()) return
        if (Server.active) {
            Server.act("group_update", JSONObject().put("groupId", groupId).put("name", cleanName).put("topic", cleanTopic))
        }
        val index = groups.indexOfFirst { it.id == groupId }
        if (index < 0) return
        val updated = groups[index].copy(name = cleanName, topic = cleanTopic, updatedAt = System.currentTimeMillis())
        groups[index] = updated
        persistGroup(updated)
    }

    /** Bring people into the room. */
    fun addGroupMembers(groupId: String, ids: List<String>) {
        val clean = ids.filter { it.isNotBlank() }.distinct()
        if (clean.isEmpty()) return
        if (Server.active) {
            Server.act("group_add", JSONObject().put("groupId", groupId).put("personaIds", JSONArray(clean)))
        }
        val index = groups.indexOfFirst { it.id == groupId }
        if (index < 0) return
        val group = groups[index]
        val updated = group.copy(members = (group.members + clean).distinct(), updatedAt = System.currentTimeMillis())
        groups[index] = updated
        persistGroup(updated)
    }

    /** Someone steps out of the room. */
    fun removeGroupMember(groupId: String, personaId: String) {
        if (Server.active) {
            Server.act("group_remove", JSONObject().put("groupId", groupId).put("personaId", personaId))
        }
        val index = groups.indexOfFirst { it.id == groupId }
        if (index < 0) return
        val group = groups[index]
        if (personaId !in group.members) return
        val updated = group.copy(members = group.members - personaId, updatedAt = System.currentTimeMillis())
        groups[index] = updated
        persistGroup(updated)
    }

    /** You send to the room; someone answers shortly after — in voice. */
    fun sendGroupMsg(groupId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        val msg = GroupMsg(
            id = if (Server.active) "s-gm-${UUID.randomUUID()}" else "local-gm-$now-${postSeq++}",
            groupId = groupId,
            authorId = "you",
            text = clean.take(600),
            at = now,
        )
        groupMsgs += msg
        persistGroupMsg(msg)
        markGroupSeen(groupId)
        if (Server.active) {
            // They're thinking over there, at their own pace. Three minutes
            // of dots, then the dots give up — an answer may still land.
            groupTyping[groupId] = true
            val mine = msg
            scope.launch {
                delay(180_000)
                val answered = groupMsgs.any { it.groupId == groupId && it.authorId != "you" && it.at > mine.at }
                if (!answered) withContext(Dispatchers.Main) { groupTyping[groupId] = false }
            }
            Server.act("group_send", JSONObject().put("id", msg.id).put("groupId", groupId).put("text", msg.text))
        } else {
            scheduleGroupReply(groupId)
        }
    }

    private fun scheduleGroupReply(groupId: String) {
        val group = groups.firstOrNull { it.id == groupId } ?: return
        val members = group.members.mapNotNull { id -> Cast.people.firstOrNull { it.id == id } }
        if (members.isEmpty()) return
        val lastText = groupMsgs.lastOrNull { it.groupId == groupId && it.authorId == "you" }?.text.orEmpty()
        val tagged = members.firstOrNull { lastText.contains("@${it.handle}", ignoreCase = true) }
        val responder = tagged ?: members.random()
        scope.launch {
            withContext(Dispatchers.Main) { groupTyping[groupId] = true }
            delay((12_000L..30_000L).random())
            val history = groupMsgs.filter { it.groupId == groupId }.takeLast(14)
                .map { (if (it.authorId == "you") "user" else "assistant") to it.text }
            val reply = Engine.chat(
                Engine.personaPrompt(responder) + groupPrompt(group, responder),
                history,
                maxTokens = 130,
                model = personaModel(responder.id),
            )
            if (reply == null) {
                withContext(Dispatchers.Main) { groupTyping[groupId] = false }
                return@launch
            }
            deliverGroupMsg(groupId, responder.id, reply)
        }
    }

    /** The room, appended to a member's system prompt. */
    private fun groupPrompt(group: Group, persona: Persona): String = buildString {
        val others = group.members.filter { it != persona.id }.map { Cast.byId(it).name }
        append("\nThis is a small group chat on Only Human called \u201C${group.name}\u201D")
        if (group.topic.isNotEmpty()) append(" — about ${group.topic}")
        append(". In the room: you")
        if (others.isNotEmpty()) append(", ${others.joinToString(", ")}")
        append(", and the human ${me.name}.")
        append(" Keep replies to one or two short sentences, lowercase where it fits, no name prefixes.")
    }

    /** Delivers an incoming group message. Safe from any thread. */
    private suspend fun deliverGroupMsg(groupId: String, personaId: String, text: String, at: Long = System.currentTimeMillis()) {
        val now = at
        val persona = Cast.byId(personaId)
        val msg = GroupMsg(
            id = "voice-gm-$now-$personaId",
            groupId = groupId,
            authorId = personaId,
            text = text,
            at = now,
        )
        val insideGroup = openGroup == groupId
        persistGroupMsg(msg)
        withContext(Dispatchers.Main) {
            groupTyping[groupId] = false
            groupMsgs += msg
            if (insideGroup) markGroupSeen(groupId)
        }
        if (!insideGroup) tell(persona.name, text, "group-$groupId")
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

    /** A story written in their voice — what they're doing right now. */
    private suspend fun writeStoryText(persona: Persona): String? =
        Engine.chat(
            Engine.personaPrompt(persona),
            listOf(
                "user" to "Write your story for the next 24 hours — the one thing you're doing or " +
                    "noticing right now, this minute. One or two very short lines, present tense, " +
                    "plain text only, no hashtags.",
            ),
            maxTokens = 100,
            model = personaModel(persona.id),
        )?.let { trimStory(it) }

    /**
     * Stories live on one screen: keep the first couple of sentences, ending
     * on a real break — never mid-word, never clipped.
     */
    private fun trimStory(raw: String): String {
        val text = raw.trim()
        if (text.length <= 220) return text
        val head = text.take(220)
        val end = head.lastIndexOfAny(charArrayOf('.', '!', '?'))
        if (end >= 60) return head.take(end + 1)
        val space = head.lastIndexOf(' ')
        return if (space > 0) head.take(space).trimEnd(',', ';', ':') else head
    }

    /**
     * Keeps stories alive the way they'd actually be: a few of the cast have
     * something current, written live at boot, each good for a day. No write,
     * no story — a failed engine call means that persona is simply quiet.
     */
    private fun refreshStories() {
        if (!onboarded || !Engine.config.ready) return
        scope.launch {
            val dao = database?.dao() ?: return@launch
            val now = System.currentTimeMillis()
            val fresh = stories
                .filter { now - it.createdAt < 24 * 60 * 60 * 1000L }
                .map { it.personaId }
                .toSet()
            val candidates = Cast.all.filter { it.id !in fresh }.shuffled().take(4)
            for (persona in candidates) {
                delay((7_000L..18_000L).random())
                val text = writeStoryText(persona) ?: continue
                val story = Story(
                    id = "story-${System.currentTimeMillis()}-${persona.id}",
                    personaId = persona.id,
                    text = text,
                    createdAt = System.currentTimeMillis(),
                    seen = false,
                )
                dao.upsertStory(story.toEntity())
                withContext(Dispatchers.Main) { stories.add(0, story) }
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
                    model = personaModel(persona.id),
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
                    model = personaModel(persona.id),
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
                    model = personaModel(persona.id),
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
            model = personaModel(persona.id),
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

    fun markGroupSeen(groupId: String) {
        val now = System.currentTimeMillis()
        groupSeen[groupId] = now
        prefs?.edit()?.putLong("gseen_$groupId", now)?.apply()
    }

    fun unreadGroupCount(groupId: String): Int {
        val seen = groupSeen[groupId] ?: 0L
        return groupMsgs.count { it.groupId == groupId && it.authorId != "you" && it.at > seen }
    }

    fun unreadGroupsTotal(): Int = groups.sumOf { unreadGroupCount(it.id) }

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

    private fun persistGroup(group: Group) {
        val db = database ?: return
        scope.launch { db.dao().upsertGroup(group.toEntity()) }
    }

    private fun persistGroupMsg(msg: GroupMsg) {
        val db = database ?: return
        scope.launch { db.dao().upsertGroupMsg(msg.toEntity()) }
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
        if (Server.active) Server.act("like", JSONObject().put("postId", postId))
    }

    fun toggleFollow(personaId: String) {
        val value = following[personaId] != true
        following[personaId] = value
        database?.let { db ->
            scope.launch {
                db.dao().upsertFollow(FollowEntity(personaId, value, followedBy[personaId] == true))
            }
        }
        if (Server.active) {
            Server.act("follow", JSONObject().put("personaId", personaId).put("on", value))
        }
    }

    fun postsBy(authorId: String): List<Post> = posts.filter { it.authorId == authorId }

    fun followerCount(persona: Persona): Int =
        if (persona.id == "you") Cast.people.size else persona.followerBoost + if (following[persona.id] == true) 1 else 0

    private fun persistPost(post: Post) {
        val db = database ?: return
        scope.launch { db.dao().upsertPost(post.toEntity()) }
    }

    // ---- the server world --------------------------------------------------

    /**
     * Adopting a server world: this phone's local world steps aside so the
     * world's own record can rebuild it cleanly from cursor zero.
     */
    suspend fun wipeForServer() {
        withContext(Dispatchers.Main) {
            posts.clear()
            replies.clear()
            dms.clear()
            groups.clear()
            groupMsgs.clear()
            stories.clear()
            activities.clear()
            commentIntents.clear()
            following.clear()
            followedBy.clear()
            Cast.setCustom(emptyList())
        }
        persistCommentIntents()
        val dao = database?.dao() ?: return
        dao.wipePosts()
        dao.wipeReplies()
        dao.wipeActivities()
        dao.wipeFollows()
        dao.wipeDms()
        dao.wipeGroups()
        dao.wipeGroupMsgs()
        dao.wipePersonas()
        dao.wipeStories()
    }

    /**
     * Everything the world sends lands here — one place, in order. Items
     * carry their own ids, so a copy that's already on screen (one you made
     * optimistically) is simply replaced, never doubled.
     */
    suspend fun applyServerSync(serverMe: JSONObject?, items: JSONArray) {
        if (!Server.active) return
        val arrivedAt = System.currentTimeMillis()
        withContext(Dispatchers.Main) {
            // Who you are over there wins — recovery restores your name too.
            if (serverMe != null) {
                val name = serverMe.optString("name").trim()
                val handle = serverMe.optString("handle").trim()
                val avatar = serverMe.optInt("avatarIndex", me.avatarIndex)
                if (name.isNotEmpty() && handle.isNotEmpty() &&
                    (name != me.name || handle != me.handle || avatar != me.avatarIndex)
                ) {
                    me = Me(name, handle, avatar)
                    prefs?.edit()
                        ?.putString(KEY_ME_NAME, name)
                        ?.putString(KEY_ME_HANDLE, handle)
                        ?.putInt(KEY_ME_AVATAR, avatar)
                        ?.apply()
                }
            }
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val data = item.optJSONObject("data") ?: continue
                applyServerItem(item.optString("kind"), data, arrivedAt)
            }
            activities.sortByDescending { it.createdAt }
        }
    }

    private fun applyServerItem(kind: String, data: JSONObject, arrivedAt: Long) {
        when (kind) {
            "post" -> {
                val id = data.optString("id")
                if (id.isEmpty()) return
                val post = Post(
                    id = id,
                    authorId = data.optString("authorId"),
                    text = data.optString("text"),
                    createdAt = data.optLong("createdAt"),
                    likes = data.optInt("likes"),
                    replies = data.optInt("replies"),
                    likedByYou = data.optBoolean("likedByMe"),
                )
                val index = posts.indexOfFirst { it.id == id }
                if (index >= 0) posts[index] = post else insertPost(post)
                persistPost(post)
            }

            "reply" -> {
                val id = data.optString("id")
                if (id.isEmpty()) return
                val reply = Reply(
                    id = id,
                    postId = data.optString("postId"),
                    authorId = data.optString("authorId"),
                    text = data.optString("text"),
                    createdAt = data.optLong("createdAt"),
                )
                if (replies.any { it.id == reply.id }) return
                replies += reply
                persistReply(reply)
                if (reply.authorId != "you" && arrivedAt - reply.createdAt < 120_000) {
                    tell(Cast.byId(reply.authorId).name, reply.text, "reply-${reply.id}")
                }
            }

            "story" -> {
                val id = data.optString("id")
                if (id.isEmpty() || stories.any { it.id == id }) return
                val story = Story(
                    id = id,
                    personaId = data.optString("personaId"),
                    text = data.optString("text"),
                    createdAt = data.optLong("createdAt"),
                    seen = false,
                )
                stories.add(0, story)
                scope.launch { database?.dao()?.upsertStory(story.toEntity()) }
            }

            "dm" -> {
                val id = data.optString("id")
                if (id.isEmpty() || dms.any { it.id == id }) return
                val dm = Dm(
                    id = id,
                    personaId = data.optString("personaId"),
                    fromMe = data.optBoolean("fromMe"),
                    text = data.optString("text"),
                    at = data.optLong("at"),
                )
                dms += dm
                persistDm(dm)
                if (!dm.fromMe) {
                    typing[dm.personaId] = false
                    if (openDm == dm.personaId) {
                        markDmSeen(dm.personaId)
                    } else {
                        tell(Cast.byId(dm.personaId).name, dm.text, "dm-${dm.id}")
                    }
                }
            }

            "group" -> {
                val id = data.optString("id")
                if (id.isEmpty()) return
                val rawMembers = data.optJSONArray("members")
                val members = if (rawMembers != null) {
                    (0 until rawMembers.length()).mapNotNull { i ->
                        rawMembers.optString(i).takeIf { it.isNotEmpty() }
                    }
                } else {
                    emptyList()
                }
                val group = Group(
                    id = id,
                    name = data.optString("name"),
                    topic = data.optString("topic"),
                    members = members,
                    createdAt = data.optLong("createdAt"),
                    updatedAt = data.optLong("updatedAt"),
                )
                val index = groups.indexOfFirst { it.id == id }
                if (index >= 0) groups[index] = group else groups += group
                persistGroup(group)
            }

            "group_msg" -> {
                val id = data.optString("id")
                if (id.isEmpty() || groupMsgs.any { it.id == id }) return
                val msg = GroupMsg(
                    id = id,
                    groupId = data.optString("groupId"),
                    authorId = data.optString("authorId"),
                    text = data.optString("text"),
                    at = data.optLong("at"),
                )
                groupMsgs += msg
                persistGroupMsg(msg)
                if (msg.authorId != "you") {
                    groupTyping[msg.groupId] = false
                    if (openGroup == msg.groupId) {
                        markGroupSeen(msg.groupId)
                    } else {
                        tell(Cast.byId(msg.authorId).name, msg.text, "group-${msg.groupId}")
                    }
                }
            }

            "ask" -> {
                val id = data.optString("id")
                if (id.isEmpty()) return
                val postId = data.optString("postId")
                val personaId = data.optString("personaId")
                val at = data.optLong("at")
                when (data.optString("state")) {
                    "pending" -> {
                        val index = commentIntents.indexOfFirst { it.id == id }
                        if (index < 0) {
                            commentIntents += CommentIntent(id = id, postId = postId, personaId = personaId, at = at)
                        } else {
                            // Back to pending after a failed write: the row
                            // offers "Try again", exactly like the local flow.
                            val wasWriting = commentIntents[index].writing
                            commentIntents[index] = commentIntents[index].copy(writing = false, failed = wasWriting)
                        }
                        persistCommentIntents()
                    }
                    "approved" -> {
                        val index = commentIntents.indexOfFirst { it.id == id }
                        if (index < 0) {
                            commentIntents += CommentIntent(
                                id = id,
                                postId = postId,
                                personaId = personaId,
                                at = at,
                                writing = true,
                            )
                        } else {
                            commentIntents[index] = commentIntents[index].copy(writing = true, failed = false)
                        }
                        persistCommentIntents()
                    }
                    else -> {
                        commentIntents.removeAll { it.id == id }
                        persistCommentIntents()
                    }
                }
            }

            "signal" -> {
                val id = data.optString("id")
                if (id.isEmpty() || activities.any { it.id == id }) return
                val signalKind = when (data.optString("kind")) {
                    "Follow" -> ActivityKind.Follow
                    "Like" -> ActivityKind.Like
                    "Reply" -> ActivityKind.Reply
                    "Mention" -> ActivityKind.Mention
                    "Message" -> ActivityKind.Message
                    else -> ActivityKind.System
                }
                val item = ActivityItem(
                    id = id,
                    personaId = data.optString("personaId"),
                    boldPart = data.optString("boldPart"),
                    rest = data.optString("rest"),
                    createdAt = data.optLong("createdAt"),
                    kind = signalKind,
                )
                activities.add(0, item)
                persistActivity(item)
            }

            "follow" -> {
                val personaId = data.optString("personaId")
                if (personaId.isEmpty()) return
                val isFollowing = data.optBoolean("following")
                val followsYou = data.optBoolean("followedBy")
                following[personaId] = isFollowing
                followedBy[personaId] = followsYou
                scope.launch {
                    database?.dao()?.upsertFollow(FollowEntity(personaId, isFollowing, followsYou))
                }
            }

            "model" -> {
                val personaId = data.optString("personaId")
                if (personaId.isEmpty()) return
                val raw = data.opt("model")
                applyPersonaModel(personaId, if (raw is String) raw.takeIf { it.isNotBlank() } else null)
            }

            "persona" -> {
                val id = data.optString("id")
                if (id.isEmpty() || Cast.residents.any { it.id == id }) return
                val persona = Persona(
                    id = id,
                    name = data.optString("name"),
                    handle = data.optString("handle"),
                    bio = data.optString("bio"),
                    occupation = data.optString("occupation"),
                    location = data.optString("location"),
                    voice = data.optString("voice"),
                    gradient = gradientFromJson(data.optJSONArray("gradient")),
                    joinedYear = data.optString("joinedYear"),
                    followerBoost = data.optInt("followerBoost"),
                )
                Cast.addCustom(persona)
                scope.launch { database?.dao()?.upsertPersona(persona.toEntity()) }
            }
        }
    }

    private fun gradientFromJson(raw: JSONArray?): List<Color> {
        if (raw == null || raw.length() == 0) return Cast.avatarPalettes.first()
        val colors = mutableListOf<Color>()
        for (i in 0 until raw.length()) {
            val hex = raw.optString(i).trim().removePrefix("#")
            val value = hex.toLongOrNull(16) ?: continue
            colors += Color((0xFF000000L or value).toInt())
        }
        return when {
            colors.size >= 2 -> colors.take(2)
            colors.size == 1 -> listOf(colors[0], colors[0])
            else -> Cast.avatarPalettes.first()
        }
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

private fun Group.toEntity() = GroupEntity(
    id = id,
    name = name,
    topic = topic,
    members = members.joinToString(","),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun GroupEntity.toModel() = Group(
    id = id,
    name = name,
    topic = topic,
    members = members.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun GroupMsg.toEntity() = GroupMsgEntity(
    id = id,
    groupId = groupId,
    authorId = authorId,
    text = text,
    at = at,
)

private fun GroupMsgEntity.toModel() = GroupMsg(
    id = id,
    groupId = groupId,
    authorId = authorId,
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

private fun Story.toEntity() = StoryEntity(
    id = id,
    personaId = personaId,
    text = text,
    createdAt = createdAt,
    seen = seen,
)

private fun StoryEntity.toModel() = Story(
    id = id,
    personaId = personaId,
    text = text,
    createdAt = createdAt,
    seen = seen,
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
