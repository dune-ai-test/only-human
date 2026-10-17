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
 * The AI engine.
 *
 * When configured, the fifteen stop drawing from [Voices] and start actually
 * writing — post replies, DMs, the welcome — through any OpenAI-compatible
 * endpoint. Kilo Gateway is pre-filled because it's the one we test against:
 * https://ai-gateway.kilo.ai/api/v1 (paste an API key in Settings).
 *
 * Offline remains a first-class state: no key, no engine, and the app behaves
 * exactly as it always has. Nothing here throws; failures fall back.
 */
object Engine {

    const val DEFAULT_ENDPOINT = "https://ai-gateway.kilo.ai/api/v1"
    const val DEFAULT_MODEL = "anthropic/claude-sonnet-4.5"

    data class Config(
        val endpoint: String = DEFAULT_ENDPOINT,
        val model: String = DEFAULT_MODEL,
        val apiKey: String = "",
        val on: Boolean = false,
    ) {
        /** The whole thing is wired up and switched on. */
        val ready: Boolean
            get() = on && apiKey.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank()
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
            on = prefs.getBoolean(KEY_ON, false),
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
        if (cfg.endpoint.isBlank() || cfg.apiKey.isBlank() || cfg.model.isBlank()) {
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
                setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                lastError = "HTTP $code — ${apiError(text)}"
                return@withContext null
            }
            val content = JSONObject(text)
                .optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content")
                ?.trim()
            if (content.isNullOrEmpty()) {
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
        if (cfg.apiKey.isBlank()) return "Add an API key first."
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
        val base = endpoint.trim().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

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
}
