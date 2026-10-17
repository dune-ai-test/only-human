package com.only.human.world

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone
import java.util.UUID

/**
 * The world server — where the world actually lives now.
 *
 * When this phone is pointed at a Worker, the world ticks over there, every
 * minute, whether the app is open or not. This side keeps three things: a
 * cache (everything synced, it's in World's lists and Room), an outbox
 * (actions that couldn't be sent yet), and one cursor (everything below it
 * is already on screen).
 *
 * The ground rules are the app's rules: real words or silence. A request
 * that fails is never faked — it queues, retries, or quietly disappears.
 */
object Server {

    /** Where this phone stands with the world. */
    sealed interface State {
        /** Not set up — the world lives on this phone alone. */
        data object Dormant : State

        /** Talking to the world; the last sync went through. */
        data object Live : State

        /** Configured, but the last attempt failed; we keep trying quietly. */
        data class Offline(val reason: String) : State
    }

    var state by mutableStateOf<State>(State.Dormant)
        private set

    /** When the last sync landed — Settings shows it as "2m ago". */
    var lastSyncAt by mutableStateOf(0L)
        private set

    /** True while a sync is in flight. */
    var syncing by mutableStateOf(false)
        private set

    /** How many of your actions are still waiting for the network. */
    var outboxCount by mutableStateOf(0)
        private set

    /** The world's daily AI-call budget, as the server knows it. */
    var budget by mutableStateOf(200)
        private set

    var baseUrl by mutableStateOf("")
        private set
    var worldId by mutableStateOf("")
        private set
    var key by mutableStateOf("")
        private set
    var cursor by mutableStateOf(0L)
        private set

    /** True when this phone is a window onto a server world. */
    val active: Boolean
        get() = baseUrl.isNotBlank() && worldId.isNotBlank() && key.isNotBlank()

    private var prefs: SharedPreferences? = null
    private var foreground = false
    private var booted = false
    private var loopRunning = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val KEY_URL = "serverUrl"
    private const val KEY_WORLD = "serverWorld"
    private const val KEY_KEY = "serverKey"
    private const val KEY_CURSOR = "serverCursor"
    private const val KEY_OUTBOX = "serverOutbox"
    private const val SYNC_EVERY_MS = 40_000L
    private const val PAGE = 300
    private const val OUTBOX_CAP = 200

    private data class OutboxEntry(val commandId: String, val action: String, val payload: String)

    private data class HttpResult(val ok: Boolean, val status: Int, val text: String, val reason: String)

    // ---- life cycle --------------------------------------------------------

    /** Restores the pointer to the world, if there is one. */
    fun load(p: SharedPreferences) {
        prefs = p
        baseUrl = p.getString(KEY_URL, "") ?: ""
        worldId = p.getString(KEY_WORLD, "") ?: ""
        key = p.getString(KEY_KEY, "") ?: ""
        cursor = p.getLong(KEY_CURSOR, 0L)
        outboxCount = loadOutbox().size
        state = if (active) State.Offline("waiting for the first sync") else State.Dormant
    }

    /** The app came on screen (or went away); syncs only run while it's here. */
    fun setForeground(value: Boolean) {
        foreground = value
        if (value) {
            startLoop()
            if (booted) kick()
        }
    }

    /** Cold boot is done: the cache is on screen, so go find out what's new. */
    fun onBoot() {
        if (!active) return
        booted = true
        startLoop()
        kick()
    }

    private fun startLoop() {
        if (loopRunning || !active) return
        loopRunning = true
        scope.launch {
            while (foreground && active) {
                delay(SYNC_EVERY_MS)
                if (foreground && active) syncNow()
            }
            loopRunning = false
        }
    }

    /** Sync right now, unless one is already running. */
    fun kick() {
        if (!active) return
        scope.launch { syncNow() }
    }

    /** Sync again in a moment — for answers that take a breath to show up. */
    fun kickIn(ms: Long) {
        if (!active) return
        scope.launch {
            delay(ms)
            syncNow()
        }
    }

    // ---- sync --------------------------------------------------------------

    private suspend fun syncNow() {
        if (!active || syncing) return
        syncing = true
        try {
            flushOutbox()
            var pages = 0
            while (pages < 12) {
                val res = request(
                    "/v1/sync",
                    JSONObject().put("worldId", worldId).put("key", key).put("cursor", cursor),
                )
                if (!res.ok) {
                    state = State.Offline(res.reason)
                    return
                }
                val json = JSONObject(res.text)
                cursor = json.optLong("cursor", cursor)
                prefs?.edit()?.putLong(KEY_CURSOR, cursor)?.apply()
                budget = json.optInt("budget", budget)
                val items = json.optJSONArray("items") ?: JSONArray()
                World.applyServerSync(json.optJSONObject("me"), items)
                lastSyncAt = System.currentTimeMillis()
                state = State.Live
                pages++
                if (items.length() < PAGE) break
                delay(200)
            }
        } finally {
            syncing = false
        }
    }

    // ---- acts --------------------------------------------------------------

    /**
     * One action on its way to the world. If the network is down it waits in
     * the outbox and goes out later — same command id, so the world can tell
     * a retry from a new act. [onRejected] fires on the main thread when the
     * action did not make it right now, delivered later or never.
     */
    fun act(action: String, payload: JSONObject, queueOnFailure: Boolean = true, onRejected: (() -> Unit)? = null) {
        if (!active) return
        val entry = OutboxEntry(UUID.randomUUID().toString(), action, payload.toString())
        scope.launch {
            val res = actRequest(entry)
            if (res.ok) return@launch
            if (res.status != 400 && queueOnFailure) enqueue(entry)
            if (res.status != 400) state = State.Offline(res.reason)
            withContext(Dispatchers.Main) { onRejected?.invoke() }
        }
    }

    private suspend fun actRequest(entry: OutboxEntry): HttpResult =
        request(
            "/v1/act",
            JSONObject()
                .put("worldId", worldId)
                .put("key", key)
                .put("action", entry.action)
                .put("commandId", entry.commandId)
                .put("payload", JSONObject(entry.payload)),
        )

    private suspend fun flushOutbox() {
        val pending = loadOutbox()
        if (pending.isEmpty()) return
        val left = mutableListOf<OutboxEntry>()
        var blocked = false
        for (entry in pending) {
            if (blocked) {
                left += entry
                continue
            }
            val res = actRequest(entry)
            when {
                res.ok -> Unit
                // The world will never want it — let it go rather than loop.
                res.status == 400 -> Unit
                else -> {
                    blocked = true
                    left += entry
                }
            }
        }
        saveOutbox(left)
    }

    private fun loadOutbox(): MutableList<OutboxEntry> {
        val raw = prefs?.getString(KEY_OUTBOX, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<OutboxEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += OutboxEntry(o.getString("c"), o.getString("a"), o.optString("p", "{}"))
            }
            list
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun saveOutbox(entries: List<OutboxEntry>) {
        val kept = entries.takeLast(OUTBOX_CAP)
        val arr = JSONArray()
        kept.forEach { e ->
            arr.put(JSONObject().put("c", e.commandId).put("a", e.action).put("p", e.payload))
        }
        prefs?.edit()?.putString(KEY_OUTBOX, arr.toString())?.apply()
        outboxCount = kept.size
    }

    private suspend fun enqueue(entry: OutboxEntry) {
        val list = loadOutbox()
        if (list.any { it.commandId == entry.commandId }) return
        list += entry
        saveOutbox(list)
    }

    // ---- setting up a world ------------------------------------------------

    /** Ships this phone's cast and name over, and the world starts living there. */
    fun create(url: String, onDone: (String?) -> Unit) {
        val clean = url.trim().trimEnd('/')
        if (clean.isEmpty()) {
            onDone("Add the server URL first")
            return
        }
        if (!World.onboarded || World.me.name.isBlank()) {
            onDone("Finish onboarding first — the world starts around your name")
            return
        }
        scope.launch {
            val res = postRaw(clean, "/v1/worlds", newWorldBody())
            if (!res.ok) {
                withContext(Dispatchers.Main) { onDone(res.reason) }
                return@launch
            }
            val created = try {
                val json = JSONObject(res.text)
                json.getString("worldId") to json.getString("key")
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onDone("The world answered something unexpected") }
                return@launch
            }
            adopt(clean, created.first, created.second)
            World.wipeForServer()
            syncNow()
            withContext(Dispatchers.Main) { onDone(null) }
        }
    }

    /** Points this phone at an existing world — the same world, any device. */
    fun recover(url: String, id: String, recoveryKey: String, onDone: (String?) -> Unit) {
        val clean = url.trim().trimEnd('/')
        val world = id.trim()
        val secret = recoveryKey.trim()
        if (clean.isEmpty()) {
            onDone("Add the server URL first")
            return
        }
        if (world.isEmpty() || secret.isEmpty()) {
            onDone("A world id and a recovery key — both of them")
            return
        }
        scope.launch {
            // Ask the world to introduce itself before this phone adopts it.
            val probe = postRaw(
                clean,
                "/v1/sync",
                JSONObject().put("worldId", world).put("key", secret).put("cursor", 0L),
            )
            if (!probe.ok) {
                withContext(Dispatchers.Main) { onDone(probe.reason) }
                return@launch
            }
            adopt(clean, world, secret)
            World.wipeForServer()
            syncNow()
            withContext(Dispatchers.Main) { onDone(null) }
        }
    }

    private suspend fun adopt(url: String, id: String, secret: String) {
        withContext(Dispatchers.Main) {
            baseUrl = url
            worldId = id
            key = secret
        }
        cursor = 0L
        prefs?.edit()
            ?.putString(KEY_URL, url)
            ?.putString(KEY_WORLD, id)
            ?.putString(KEY_KEY, secret)
            ?.putLong(KEY_CURSOR, 0L)
            ?.apply()
        booted = true
        startLoop()
    }

    /** Steps back to the local world — the server keeps its own copy. */
    fun disconnect() {
        worldId = ""
        key = ""
        cursor = 0L
        prefs?.edit()
            ?.remove(KEY_WORLD)
            ?.remove(KEY_KEY)
            ?.remove(KEY_CURSOR)
            ?.remove(KEY_OUTBOX)
            ?.apply()
        outboxCount = 0
        state = State.Dormant
    }

    fun deleteWorld(onDone: (String?) -> Unit) {
        if (!active) {
            onDone(null)
            return
        }
        scope.launch {
            val res = request(
                "/v1/delete",
                JSONObject().put("worldId", worldId).put("key", key).put("confirm", "delete"),
            )
            if (!res.ok && res.status != 404) {
                withContext(Dispatchers.Main) { onDone(res.reason) }
                return@launch
            }
            disconnect()
            withContext(Dispatchers.Main) { onDone(null) }
        }
    }

    fun exportWorld(onDone: (String?, String?) -> Unit) {
        if (!active) {
            onDone(null, "No world to export")
            return
        }
        scope.launch {
            val res = request("/v1/export", JSONObject().put("worldId", worldId).put("key", key))
            if (!res.ok) {
                withContext(Dispatchers.Main) { onDone(null, res.reason) }
                return@launch
            }
            withContext(Dispatchers.Main) { onDone(res.text, null) }
        }
    }

    fun setBudget(value: Int) {
        val clean = value.coerceIn(0, 1000)
        budget = clean
        act("budget", JSONObject().put("value", clean))
    }

    // ---- the wire ----------------------------------------------------------

    private suspend fun request(path: String, body: JSONObject): HttpResult =
        postRaw(baseUrl, path, body)

    private suspend fun postRaw(base: String, path: String, body: JSONObject): HttpResult =
        withContext(Dispatchers.IO) {
            val clean = base.trim().trimEnd('/')
            if (clean.isEmpty()) return@withContext HttpResult(false, 0, "", "Add the server URL first")
            try {
                val conn = (URL(clean + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 45_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                when {
                    code in 200..299 -> HttpResult(true, code, text, "")
                    code == 401 -> HttpResult(false, code, text, "The world didn't recognize this key")
                    code == 404 -> HttpResult(false, code, text, "Nothing answered at that address")
                    else -> HttpResult(false, code, text, "The world said $code")
                }
            } catch (e: Exception) {
                HttpResult(false, 0, "", e.message?.take(140) ?: "The network didn't answer")
            }
        }

    /**
     * The opening payload: who you are, where the clock lives, the whole cast,
     * how everyone knows each other, and the follow graph as it stands today.
     */
    private fun newWorldBody(): JSONObject {
        val personas = JSONArray()
        Cast.people.forEach { personas.put(personaPayload(it)) }
        val relations = JSONArray()
        Cast.relationships.forEach {
            relations.put(JSONObject().put("a", it.a).put("b", it.b).put("label", it.label))
        }
        val followingMap = JSONObject()
        World.following.forEach { (id, v) -> followingMap.put(id, v) }
        val followedByMap = JSONObject()
        World.followedBy.forEach { (id, v) -> followedByMap.put(id, v) }
        return JSONObject()
            .put(
                "me",
                JSONObject()
                    .put("name", World.me.name)
                    .put("handle", World.me.handle)
                    .put("avatarIndex", World.me.avatarIndex),
            )
            .put("utcOffsetMinutes", TimeZone.getDefault().rawOffset / 60_000)
            .put("personas", personas)
            .put("relations", relations)
            .put("following", followingMap)
            .put("followedBy", followedByMap)
    }

    /** A persona as the world wants it. */
    fun personaPayload(persona: Persona): JSONObject {
        val gradient = JSONArray()
        persona.gradient.take(2).forEach { gradient.put(hex(it)) }
        return JSONObject()
            .put("id", persona.id)
            .put("name", persona.name)
            .put("handle", persona.handle)
            .put("bio", persona.bio)
            .put("occupation", persona.occupation)
            .put("location", persona.location)
            .put("voice", persona.voice)
            .put("gradient", gradient)
            .put("joinedYear", persona.joinedYear)
            .put("followerBoost", persona.followerBoost)
    }

    private fun hex(color: Color): String =
        "#" + "%06X".format(color.toArgb() and 0xFFFFFF)
}
