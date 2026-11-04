package com.example

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ScoreResponse(
    val sport: String,
    val status: String,
    val players: List<String>,
    val server: Int,
    val winner: Int? = null,
    val sets: List<Int>? = null,
    @SerialName("sets_won") val setsWon: List<Int>? = null,
    @SerialName("current_set") val currentSet: Int? = null,
    val points: List<Int>? = null,
    @SerialName("points_display") val pointsDisplay: List<String>? = null,
    val games: List<Int>? = null
) {
    val isFinished: Boolean get() = status == "FINISHED"
    val player1: String get() = players.getOrElse(0) { "" }
    val player2: String get() = players.getOrElse(1) { "" }
}

@Serializable
private data class StartMatchRequest(
    val sport: String,
    val player1: String,
    val player2: String,
    @SerialName("server_index") val serverIndex: Int
)

@Serializable
private data class AddPointRequest(
    @SerialName("player_index") val playerIndex: Int
)

@Serializable
data class VolleyballSetScore(
    val sets: List<Int>,
    @SerialName("sets_won") val setsWon: List<Int>,
    @SerialName("current_set") val currentSet: Int,
    val server: Int,
    val status: String,
    val winner: Int? = null
)

@Serializable
data class TennisSetScore(
    val points: List<Int>,
    val games: List<Int>,
    @SerialName("sets_won") val setsWon: List<Int>,
    val server: Int,
    val status: String,
    val winner: Int? = null
)

@Serializable
private data class TextRequest(val text: String)

@Serializable
private data class TextResponse(val message: String)

object ApiClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = true; encodeDefaults = true }
    private val client = HttpClient(io.ktor.client.engine.cio.CIO) {
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
    }

    private const val BASE_URL = "http://127.0.0.1:8000/api/match"
    private const val WS_URL   = "ws://127.0.0.1:8000/ws/match"

    private fun <T> call(block: suspend () -> T, onResult: (T?) -> Unit, onError: ((String) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                onResult(block())
            } catch (e: ClientRequestException) {
                val text = e.response.bodyAsText()
                val detail = runCatching { json.parseToJsonElement(text).jsonObject["detail"]?.jsonPrimitive?.content }.getOrNull()
                val msg = detail ?: "HTTP ${e.response.status.value}: $text"
                onError?.invoke(msg); onResult(null)
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "Сеть недоступна"); onResult(null)
            }
        }
    }

    fun startMatch(sport: String, p1: String, p2: String, server: Int, onResult: (ScoreResponse?) -> Unit, onError: ((String) -> Unit)? = null) = call({
        client.post("$BASE_URL/start") { contentType(ContentType.Application.Json); setBody(StartMatchRequest(sport, p1, p2, server)) }.body()
    }, onResult, onError)

    fun addPoint(playerIndex: Int, onResult: (ScoreResponse?) -> Unit, onError: ((String) -> Unit)? = null) = call({
        client.post("$BASE_URL/point") { contentType(ContentType.Application.Json); setBody(AddPointRequest(playerIndex)) }.body()
    }, onResult, onError)

    fun removePoint(onResult: (ScoreResponse?) -> Unit, onError: ((String) -> Unit)? = null) = call({ client.post("$BASE_URL/remove-point").body() }, onResult, onError)
    fun reset(onResult: (ScoreResponse?) -> Unit, onError: ((String) -> Unit)? = null) = call({ client.post("$BASE_URL/reset").body() }, onResult, onError)
    fun forceEnd(onResult: (ScoreResponse?) -> Unit, onError: ((String) -> Unit)? = null) = call({ client.post("$BASE_URL/force-end").body() }, onResult, onError)
    fun getState(onResult: (ScoreResponse?) -> Unit, onError: ((String) -> Unit)? = null) = call({ client.get("$BASE_URL/state").body() }, onResult, onError)

    fun setScore(state: VolleyballSetScore, onResult: (ScoreResponse?) -> Unit, onError: ((String) -> Unit)? = null) = call({
        client.post("$BASE_URL/set-score") { contentType(ContentType.Application.Json); setBody(state) }.body()
    }, onResult, onError)

    fun setScore(state: TennisSetScore, onResult: (ScoreResponse?) -> Unit, onError: ((String) -> Unit)? = null) = call({
        client.post("$BASE_URL/set-score") { contentType(ContentType.Application.Json); setBody(state) }.body()
    }, onResult, onError)

    fun sendText(text: String, onResult: (Boolean) -> Unit, onError: ((String) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val r: TextResponse = client.post("$BASE_URL/text") { contentType(ContentType.Application.Json); setBody(TextRequest(text)) }.body()
                onResult(r.message == "Text broadcasted")
            } catch (e: Exception) { onError?.invoke(e.message ?: "Ошибка"); onResult(false) }
        }
    }

    // ── WebSocket: одно соединение, любое число подписчиков (предпросмотр + табло на ТВ) ──
    private class WsListener(val onScore: (ScoreResponse) -> Unit, val onText: (String) -> Unit)

    private val wsListeners = java.util.concurrent.CopyOnWriteArrayList<WsListener>()
    private var wsJob: Job? = null

    /** Подписывается на счёт и текст. Возвращает функцию отписки; при отписке последнего соединение закрывается. */
    @Synchronized
    fun subscribeWs(onScore: (ScoreResponse) -> Unit, onText: (String) -> Unit = {}): () -> Unit {
        val listener = WsListener(onScore, onText)
        wsListeners += listener
        if (wsJob?.isActive != true) {
            wsJob = CoroutineScope(Dispatchers.IO).launch {
                // Переподключение: бэкенд может подняться позже окна или перезапуститься.
                while (isActive) {
                    try {
                        client.webSocket(WS_URL) {
                            for (frame in incoming) {
                                if (frame !is Frame.Text) continue
                                val root = runCatching { json.parseToJsonElement(frame.readText()).jsonObject }.getOrNull() ?: continue
                                when (root["type"]?.jsonPrimitive?.content) {
                                    "score" -> root["data"]
                                        ?.let { runCatching { json.decodeFromJsonElement<ScoreResponse>(it) }.getOrNull() }
                                        ?.let { r -> wsListeners.forEach { it.onScore(r) } }
                                    "text" -> root["data"]?.jsonPrimitive?.content
                                        ?.let { t -> wsListeners.forEach { it.onText(t) } }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }
                    delay(1_500)
                }
            }
        }
        return {
            synchronized(this) {
                wsListeners -= listener
                if (wsListeners.isEmpty()) { wsJob?.cancel(); wsJob = null }
            }
        }
    }
}