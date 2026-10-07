package com.iron.fitness.core.ai

import android.util.Base64
import com.iron.fitness.core.settings.AppSettings
import com.iron.fitness.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Ошибка обращения к Gemini (сообщение пользователю — по [kind]). */
class GeminiException(val kind: Kind, message: String? = null) : Exception(message) {
    enum class Kind { NO_KEY, INVALID_KEY, QUOTA, OVERLOADED, MODEL_NOT_FOUND, BLOCKED, NETWORK, BAD_RESPONSE }
}

/** Картинка для запроса. */
class InlineImage(val bytes: ByteArray, val mimeType: String = "image/jpeg")

/**
 * Единый сетевой слой ассистента: REST generateContent Gemini API.
 * Ключ — из [SecretStore], модель — из настроек. Отправляется только то, что передано в запросе.
 */
@Singleton
class GeminiClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
) {
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    private suspend fun model(): String = settings.current().geminiModel.trim().removePrefix("models/").ifBlank { DEFAULT_MODEL }

    private fun key(): String = secrets.geminiKey()?.takeIf { it.isNotBlank() } ?: throw GeminiException(GeminiException.Kind.NO_KEY)

    /** Проверка ключа и модели: запрос описания модели. */
    suspend fun ping(): String = withContext(Dispatchers.IO) {
        val k = key()
        val m = model()
        val request = Request.Builder()
            .url("$BASE/models/${URLEncoder.encode(m, "UTF-8")}")
            .header("x-goog-api-key", k)
            .get()
            .build()
        execute(request)
        m
    }

    /** Текстовый ответ. [schema] — OpenAPI-схема ответа (тогда ответ — JSON). */
    suspend fun generate(
        prompt: String,
        system: String? = null,
        schema: JsonObject? = null,
        images: List<InlineImage> = emptyList(),
        temperature: Double = 0.4,
    ): String = withContext(Dispatchers.IO) {
        val k = key()
        val m = model()
        val body = buildJsonObject {
            if (system != null) {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", system) } }
                }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        images.forEach { img ->
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", img.mimeType)
                                    put("data", Base64.encodeToString(img.bytes, Base64.NO_WRAP))
                                }
                            }
                        }
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                if (schema != null) {
                    put("responseMimeType", "application/json")
                    put("responseSchema", schema)
                }
            }
        }
        val request = Request.Builder()
            .url("$BASE/models/${URLEncoder.encode(m, "UTF-8")}:generateContent")
            .header("x-goog-api-key", k)
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(mediaJson))
            .build()
        val raw = execute(request)
        extractText(raw)
    }

    /** Ответ по схеме, разобранный в [serializer]. */
    suspend fun <T> generateJson(
        prompt: String,
        serializer: KSerializer<T>,
        schema: JsonObject,
        system: String? = null,
        images: List<InlineImage> = emptyList(),
        temperature: Double = 0.2,
    ): T {
        val text = generate(prompt, system, schema, images, temperature)
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { json.decodeFromString(serializer, cleaned) }
            .getOrElse { throw GeminiException(GeminiException.Kind.BAD_RESPONSE, it.message) }
    }

    private fun execute(request: Request): String {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw GeminiException(GeminiException.Kind.NETWORK, e.message)
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (r.isSuccessful) return text
            val status = runCatching {
                json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            val kind = when {
                r.code == 400 && (text.contains("API_KEY_INVALID") || text.contains("API key not valid")) -> GeminiException.Kind.INVALID_KEY
                r.code == 401 || r.code == 403 -> GeminiException.Kind.INVALID_KEY
                r.code == 404 -> GeminiException.Kind.MODEL_NOT_FOUND
                r.code == 429 || status == "RESOURCE_EXHAUSTED" -> GeminiException.Kind.QUOTA
                r.code == 503 || r.code == 500 || status == "UNAVAILABLE" -> GeminiException.Kind.OVERLOADED
                else -> GeminiException.Kind.BAD_RESPONSE
            }
            throw GeminiException(kind, "HTTP ${r.code}")
        }
    }

    private fun extractText(raw: String): String {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw GeminiException(GeminiException.Kind.BAD_RESPONSE)
        val candidates = root["candidates"]?.jsonArray
        if (candidates.isNullOrEmpty()) {
            val blocked = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
            throw GeminiException(if (blocked != null) GeminiException.Kind.BLOCKED else GeminiException.Kind.BAD_RESPONSE, blocked)
        }
        val first = candidates[0].jsonObject
        val parts = first["content"]?.jsonObject?.get("parts")?.jsonArray ?: JsonArray(emptyList())
        // Части-«размышления» модели пропускаем, берём только ответ.
        val text = parts.mapNotNull { p ->
            val o = p.jsonObject
            if (o["thought"]?.jsonPrimitive?.booleanOrNull == true) null else o["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("")
        if (text.isBlank()) {
            val reason = first["finishReason"]?.jsonPrimitive?.contentOrNull
            throw GeminiException(if (reason == "SAFETY") GeminiException.Kind.BLOCKED else GeminiException.Kind.BAD_RESPONSE, reason)
        }
        return text
    }

    companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_MODEL = AppSettings.DEFAULT_GEMINI_MODEL
    }
}

/** Мини-конструктор схем ответа (OpenAPI-подмножество Gemini). */
object Schema {
    fun str(description: String? = null, enum: List<String>? = null): JsonObject = buildJsonObject {
        put("type", "STRING")
        description?.let { put("description", it) }
        enum?.let { values -> putJsonArray("enum") { values.forEach { add(it) } } }
    }

    fun num(nullable: Boolean = true, description: String? = null): JsonObject = buildJsonObject {
        put("type", "NUMBER")
        if (nullable) put("nullable", true)
        description?.let { put("description", it) }
    }

    fun int(nullable: Boolean = true, description: String? = null): JsonObject = buildJsonObject {
        put("type", "INTEGER")
        if (nullable) put("nullable", true)
        description?.let { put("description", it) }
    }

    fun bool(description: String? = null): JsonObject = buildJsonObject {
        put("type", "BOOLEAN")
        description?.let { put("description", it) }
    }

    fun arr(items: JsonObject): JsonObject = buildJsonObject {
        put("type", "ARRAY")
        put("items", items)
    }

    fun obj(vararg props: Pair<String, JsonElement>, required: List<String> = emptyList()): JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
        if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(it) } })
    }
}
