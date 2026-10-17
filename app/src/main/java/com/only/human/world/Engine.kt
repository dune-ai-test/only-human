package com.only.human.world

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The AI engine — the only source of words on Only Human.
 *
 * Every post, reply and DM is written here, through any OpenAI-compatible
 * endpoint. Kilo's gateway is pre-filled out of the box (free models, no
 * key needed): https://api.kilo.ai/api/gateway. The key stays optional —
 * local servers (Ollama, LM Studio, llama.cpp) mostly don't want one; a
 * blank key just means no Authorization header. The model list is fetched
 * from the endpoint's /models and cached on this device.
 *
 * Nothing here throws. When a write fails, the moment it belonged to
 * quietly doesn't happen — there is no canned fallback.
 */
object Engine {

    const val DEFAULT_ENDPOINT = "https://api.kilo.ai/api/gateway"
    const val DEFAULT_MODEL = "poolside/laguna-s-2.1:free"

    data class Config(
        val endpoint: String = DEFAULT_ENDPOINT,
        val model: String = DEFAULT_MODEL,
        val apiKey: String = "",
        val on: Boolean = true,
    ) {
        /** The whole thing is wired up and switched on. A key is optional. */
        val ready: Boolean
            get() = on && endpoint.isNotBlank() && model.isNotBlank()
    }

    var config by mutableStateOf(Config())
        private set

    /** Set when the last request failed; cleared by the next success. */
    var lastError by mutableStateOf<String?>(null)
        private set

    private var prefs: SharedPreferences? = null

    fun load(prefs: SharedPreferences) {
        this.prefs = prefs
        config = Config(
            endpoint = prefs.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT,
            model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
            apiKey = prefs.getString(KEY_KEY, "") ?: "",
            on = prefs.getBoolean(KEY_ON, true),
        )
    }

    fun save(next: Config) {
        config = next
        prefs?.edit()
            ?.putString(KEY_ENDPOINT, next.endpoint)
            ?.putString(KEY_MODEL, next.model)
            ?.putString(KEY_KEY, next.apiKey)
            ?.putBoolean(KEY_ON, next.on)
            ?.apply()
    }

    /**
     * One chat completion. Returns the assistant text, or null — [lastError]
     * carries the human-readable reason. Never throws.
     */
    suspend fun chat(
        system: String,
        history: List<Pair<String, String>>,   // "user"/"assistant" to content
        maxTokens: Int = 160,
        temperature: Double = 0.9,
        requireEnabled: Boolean = true,
    ): String? = withContext(Dispatchers.IO) {
        val cfg = config
        if (requireEnabled && !cfg.ready) return@withContext null
        if (cfg.endpoint.isBlank() || cfg.model.isBlank()) {
            lastError = "Engine not configured"
            return@withContext null
        }
        val body = JSONObject().apply {
            put("model", cfg.model)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    history.forEach { (role, content) ->
                        put(JSONObject().put("role", role).put("content", content))
                    }
                },
            )
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("stream", false)
        }
        try {
            val conn = (URL(chatUrl(cfg.endpoint)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (cfg.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
                }
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                lastError = "HTTP $code — ${apiError(text)}"
                return@withContext null
            }
            // A JSON null (or the literal "null" org.json hands back for one)
            // means the model produced nothing visible — treat it as a miss.
            val content = JSONObject(text)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != "null" }
            if (content == null) {
                lastError = "Empty response"
                return@withContext null
            }
            lastError = null
            sanitize(content)
        } catch (e: Exception) {
            lastError = e.message?.take(140) ?: "Network error"
            null
        }
    }

    /** Round-trips a tiny prompt. Returns one line for the settings screen. */
    suspend fun test(): String {
        val cfg = config
        if (cfg.endpoint.isBlank() || cfg.model.isBlank()) return "Endpoint and model are required."
        val reply = chat(
            system = "You are a connectivity check. Follow the instruction exactly.",
            history = listOf("user" to "Reply with exactly: it works"),
            maxTokens = 16,
            temperature = 0.0,
            requireEnabled = false,
        )
        return if (reply != null) {
            "Connected — they said: \u201C$reply\u201D"
        } else {
            "Failed: ${lastError ?: "unknown error"}"
        }
    }

    /**
     * GET {endpoint}/models — the standard OpenAI model listing. Returns the
     * sorted ids, or null — [lastError] carries the human-readable reason.
     * Never throws.
     */
    suspend fun fetchModels(endpoint: String, apiKey: String): List<String>? = withContext(Dispatchers.IO) {
        val base = normalize(endpoint)
        if (base.isEmpty()) {
            lastError = "Add an endpoint first."
            return@withContext null
        }
        try {
            val conn = (URL("$base/models").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 30_000
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                lastError = "HTTP $code — ${apiError(text)}"
                return@withContext null
            }
            val arr = JSONObject(text).optJSONArray("data")
            if (arr == null) {
                lastError = "Unexpected response — no model list"
                return@withContext null
            }
            val ids = buildList {
                for (i in 0 until arr.length()) {
                    val id = arr.optJSONObject(i)?.optString("id")?.trim().orEmpty()
                    if (id.isNotEmpty()) add(id)
                }
            }.distinct().sorted()
            if (ids.isEmpty()) {
                lastError = "The endpoint lists no models"
                return@withContext null
            }
            lastError = null
            ids
        } catch (e: Exception) {
            lastError = e.message?.take(140) ?: "Network error"
            null
        }
    }

    /** The model list cached for [endpoint] — empty when none (or the endpoint changed). */
    fun cachedModels(endpoint: String): List<String> {
        val p = prefs ?: return emptyList()
        val forEndpoint = p.getString(KEY_MODELS_ENDPOINT, null) ?: return emptyList()
        if (forEndpoint != normalize(endpoint)) return emptyList()
        val json = p.getString(KEY_MODELS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i).trim()
                    if (id.isNotEmpty()) add(id)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Keeps the fetched list around for next time. */
    fun cacheModels(endpoint: String, models: List<String>) {
        prefs?.edit()
            ?.putString(KEY_MODELS_ENDPOINT, normalize(endpoint))
            ?.putString(KEY_MODELS, JSONArray(models).toString())
            ?.apply()
    }

    // ---- persona prompting -------------------------------------------------

    /** The system prompt that makes [persona] sound like themselves. */
    fun personaPrompt(persona: Persona): String {
        val me = World.me
        val ties = Cast.relationships
            .filter { it.a == persona.id || it.b == persona.id }
            .joinToString("\n") { rel ->
                val otherId = if (rel.a == persona.id) rel.b else rel.a
                "- ${Cast.byId(otherId).name}: ${rel.label}"
            }
        return buildString {
            append("You are ${persona.name}, @${persona.handle}, on a tiny social app called Only Human.\n")
            append("About you: ${persona.bio}\n")
            if (persona.occupation.isNotEmpty()) {
                append("What you do: ${persona.occupation}")
                if (persona.location.isNotEmpty()) append(", living in ${persona.location}")
                append(" — it colors what you notice and what you post about.\n")
            }
            append("Your writing style — this defines you: ${persona.voice}.\n")
            append("The person you're writing to is ${me.name}, @${me.handle} — the only human on the app, and your new neighbor. Everyone else here is part of your small circle of friends.\n")
            if (ties.isNotEmpty()) append("People you know:\n$ties\n")
            append("Rules, always: write like a real person — never mention AI, models, assistants or prompts; no hashtags; lowercase is natural; no emoji unless your style calls for it; one or two short sentences; never echo the message back; never explain the joke.")
        }
    }

    /** [personaPrompt] plus a nudge that this is a private conversation. */
    fun dmPrompt(persona: Persona): String =
        personaPrompt(persona) +
            "\nThis is a private DM conversation. DMs are more relaxed than posts: ask things, reference your day, keep it short."

    // ---- internals ---------------------------------------------------------

    private fun chatUrl(endpoint: String): String {
        val base = normalize(endpoint)
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    private fun normalize(endpoint: String): String = endpoint.trim().trimEnd('/')

    private fun apiError(body: String): String = try {
        val obj = JSONObject(body)
        val err = obj.optJSONObject("error")
        when {
            err?.has("message") == true -> err.getString("message").take(140)
            obj.has("message") -> obj.getString("message").take(140)
            else -> body.take(140)
        }
    } catch (_: Exception) {
        body.take(140)
    }

    /** One line, no quotes, no newlines, capped — the way they'd actually send it. */
    private fun sanitize(text: String): String {
        val flat = text
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .trim('"', '\u201C', '\u201D', ' ')
        return if (flat.length <= 280) flat else flat.take(277).trimEnd() + "\u2026"
    }

    private const val KEY_ENDPOINT = "engineEndpoint"
    private const val KEY_MODEL = "engineModel"
    private const val KEY_KEY = "engineKey"
    private const val KEY_ON = "engineOn"
    private const val KEY_MODELS = "engineModels"
    private const val KEY_MODELS_ENDPOINT = "engineModelsEndpoint"
}
