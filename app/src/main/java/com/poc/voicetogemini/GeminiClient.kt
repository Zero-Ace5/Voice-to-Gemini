package com.poc.voicetogemini

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiClient(private val context: Context) {

    companion object {
        private const val TAG = "GeminiClient"
        private const val PREFS_NAME = "voice_gemini_prefs"
        private const val KEY_API_KEY = "gemini_api_key"

        const val MODEL_PRIMARY = "gemini-3.5-flash-lite"
        const val MODEL_FALLBACK = "gemini-3.1-flash-lite"

        private const val SYSTEM_PROMPT =
            "You are an expert speech-to-text editor. Your only job is to clean and polish spoken transcripts. " +
            "Rules:\n" +
            "1. Remove all vocal disfluencies, stuttering, and filler words (e.g. 'um', 'uh', 'like', 'you know', 'basically', 'so yeah').\n" +
            "2. Fix punctuation, capitalization, sentence structure, and obvious speech recognition errors.\n" +
            "3. Preserve the speaker's original meaning, tone, and exact language.\n" +
            "4. Do NOT answer questions, comment, explain, or add conversational pleasantries.\n" +
            "5. Return ONLY the final polished text with no surrounding quotes or markdown code blocks."
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getSavedApiKey(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun saveApiKey(apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    suspend fun polishText(rawTranscript: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getSavedApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is missing. Set it in app settings."))
        }

        if (rawTranscript.isBlank()) {
            return@withContext Result.success("")
        }

        // Try primary model first (gemini-3.5-flash-lite)
        val primaryResult = executeGeminiRequest(MODEL_PRIMARY, apiKey, rawTranscript)
        if (primaryResult.isSuccess) {
            return@withContext primaryResult
        }

        Log.w(TAG, "Primary model ($MODEL_PRIMARY) failed: ${primaryResult.exceptionOrNull()?.message}. Attempting fallback ($MODEL_FALLBACK)...")

        // Try fallback model (gemini-3.1-flash-lite)
        val fallbackResult = executeGeminiRequest(MODEL_FALLBACK, apiKey, rawTranscript)
        if (fallbackResult.isSuccess) {
            return@withContext fallbackResult
        }

        return@withContext Result.failure(
            Exception("Both models failed. Primary ($MODEL_PRIMARY): ${primaryResult.exceptionOrNull()?.message}; Fallback ($MODEL_FALLBACK): ${fallbackResult.exceptionOrNull()?.message}")
        )
    }

    private fun executeGeminiRequest(model: String, apiKey: String, text: String): Result<String> {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            // Construct Gemini REST JSON Payload
            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val messageObj = JSONObject().apply {
                        put("role", "user")
                        val partsArray = JSONArray().apply {
                            val partObj = JSONObject().apply {
                                put("text", "Clean and format this transcript:\n\n$text")
                            }
                            put(partObj)
                        }
                        put("parts", partsArray)
                    }
                    put(messageObj)
                }
                put("contents", contentsArray)

                // System Instruction
                val systemInstructionObj = JSONObject().apply {
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply { put("text", SYSTEM_PROMPT) })
                    }
                    put("parts", partsArray)
                }
                put("systemInstruction", systemInstructionObj)

                // Generation Config for low latency and deterministic formatting
                val genConfig = JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 2048)
                }
                put("generationConfig", genConfig)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return Result.failure(Exception("No candidates returned from Gemini"))
            }

            val content = candidates.getJSONObject(0).optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val outputText = parts?.getJSONObject(0)?.optString("text", "")?.trim() ?: ""

            Result.success(outputText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
