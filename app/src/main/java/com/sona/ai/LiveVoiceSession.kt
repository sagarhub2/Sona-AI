package com.sona.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gemini Live audio prototype. Public releases should use short-lived Live API
 * tokens issued by an authenticated backend instead of a permanent client API key.
 */
class LiveVoiceSession(
    private val apiKey: String,
    private val onStatus: (String) -> Unit,
    private val onTranscript: (String) -> Unit
) {
    private val model = "gemini-3.1-flash-live-preview"
    @Volatile private var turnReceivedAudio = false
    @Volatile private var pendingOutputTranscript = ""
    private val running = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)
    private val setupCompleted = AtomicBoolean(false)
    private val audioStarted = AtomicBoolean(false)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var player: AudioTrack? = null
    @Volatile private var micThread: Thread? = null

    /** Stop sending captured microphone audio to Gemini while muted. */
    fun setMuted(mute: Boolean) {
        muted.set(mute)
        onStatus(if (mute) "MICROPHONE MUTED" else "LIVE • SPEAK TO MYRA")
    }

    /**
     * Update the voice conversation's style without reconnecting the audio session.
     * Gemini Live accepts client content turns while the WebSocket session is active.
     */
    fun setBossMode(enabled: Boolean): Boolean {
        // A session object can exist while the WebSocket is still connecting.
        // Do not report the mode as active until Gemini Live setup has completed.
        if (!running.get() || !setupCompleted.get()) {
            onStatus("BOSS MODE • WAITING FOR VOICE CONNECTION")
            return false
        }
        val instruction = if (enabled) {
            "System-style preference for the rest of this conversation: Boss Mode is ON. Be proactive, concise, confident and action-oriented. Offer clear next steps. Always ask for confirmation before sensitive actions or device-changing actions."
        } else {
            "System-style preference for the rest of this conversation: Boss Mode is OFF. Return to a warm, friendly, conversational assistant style."
        }
        val message = JSONObject().put("clientContent", JSONObject()
            .put("turns", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", instruction)))))
            .put("turnComplete", true))
        val sent = socket?.send(message.toString()) == true
        if (!sent) onStatus("BOSS MODE • WAITING FOR VOICE CONNECTION")
        else onStatus(if (enabled) "BOSS MODE ACTIVE" else "LIVE • SPEAK TO MYRA")
        return sent
    }

    fun start() {
        if (apiKey.isBlank()) { onStatus("ADD GEMINI KEY IN SETTINGS"); return }
        if (!running.compareAndSet(false, true)) { onStatus("LIVE VOICE IS ALREADY RUNNING"); return }
        // Reset per-session state so a reconnect cannot inherit a previous setup/turn.
        setupCompleted.set(false)
        audioStarted.set(false)
        muted.set(false)
        turnReceivedAudio = false
        pendingOutputTranscript = ""
        onStatus("CONNECTING TO GEMINI LIVE…")
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + URLEncoder.encode(apiKey, "UTF-8")
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                // Configure Gemini Live audio output, voice, and transcription settings.
                val setup = JSONObject().put("setup", JSONObject()
                    .put("model", "models/$model")
                    .put("generationConfig", JSONObject()
                        .put("responseModalities", JSONArray().put("AUDIO"))
                        .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                            .put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Aoede")))))
                    .put("inputAudioTranscription", JSONObject())
                    .put("outputAudioTranscription", JSONObject())
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                        "You are Myra, using a natural real-time voice inspired by the user's reference design. Speak warmly in Hindi or Hinglish like a helpful Indian friend. Keep replies conversational and concise."))))
                    )
                val sent = ws.send(setup.toString())
                if (!sent) {
                    onStatus("SETUP SEND FAILED • RETRY VOICE")
                    stopAudio()
                    ws.cancel()
                    return
                }
                onStatus("CONNECTED • WAITING FOR GEMINI SETUP…")
                Thread({
                    try { Thread.sleep(12000) } catch (_: InterruptedException) { return@Thread }
                    if (running.get() && !setupCompleted.get()) {
                        onStatus("VOICE SETUP TIMEOUT • NO GEMINI SETUP REPLY")
                        stopAudio()
                        socket?.cancel()
                    }
                }, "Sona-Setup-Watchdog").apply { isDaemon = true; start() }
            }
            override fun onMessage(ws: WebSocket, bytes: okio.ByteString) {
                // Gemini may send JSON control messages in binary frames too.
                onMessage(ws, bytes.utf8())
            }
            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    if (root.has("error")) {
                        val error = root.optJSONObject("error")
                        onStatus("GEMINI ERROR: " + (error?.optString("message") ?: "Unknown setup error").take(140))
                        stopAudio()
                        ws.close(1000, "Gemini returned an error")
                        return
                    }
                    if (root.has("setupComplete")) {
                        setupCompleted.set(true)
                        startAudio()
                    }
                    val server = root.optJSONObject("serverContent")
                    server?.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotBlank() && it != "null" }?.let { onTranscript("You: $it") }
                    server?.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotBlank() && it != "null" }?.let { text ->
                        pendingOutputTranscript = (if (pendingOutputTranscript.isBlank()) text else if (text.startsWith(pendingOutputTranscript)) text else pendingOutputTranscript + text).takeLast(4000)
                        // Keep a bounded cumulative transcript; the API may deliver it in chunks.
                        onTranscript(pendingOutputTranscript)
                    }
                    val parts = server?.optJSONObject("modelTurn")?.optJSONArray("parts")
                    if (parts != null) for (i in 0 until parts.length()) {
                        val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data")
                        if (!data.isNullOrBlank()) {
                            // A normal PCM chunk is small; reject oversized payloads before decoding.
                            if (data.length > 180_000) {
                                onStatus("LIVE AUDIO CHUNK TOO LARGE • IGNORED")
                                continue
                            }
                            val bytes = Base64.decode(data, Base64.DEFAULT)
                            turnReceivedAudio = true
                            player?.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING)
                        }
                    }
                    if (server?.optBoolean("interrupted", false) == true) {
                        player?.pause(); player?.flush(); player?.play()
                        pendingOutputTranscript = ""
                        turnReceivedAudio = false
                    }
                    if (server?.optBoolean("turnComplete", false) == true) {
                        // Only use Android TTS when Gemini returned no playable audio for this turn.
                        if (!turnReceivedAudio && pendingOutputTranscript.isNotBlank()) {
                            onTranscript("SPEAK_FALLBACK:" + pendingOutputTranscript)
                        }
                        pendingOutputTranscript = ""
                        turnReceivedAudio = false
                    }
                } catch (e: Exception) { onStatus("LIVE RESPONSE ERROR • " + (e.message ?: "INVALID SERVER MESSAGE").take(100)) }
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                stopAudio()
                onStatus("LIVE CONNECTION FAILED • " + ((response?.code?.let { "HTTP $it • " } ?: "") + (t.message ?: "CHECK KEY / NETWORK")).take(110))
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                val hadSetup = setupCompleted.get()
                stopAudio()
                if (hadSetup) {
                    onStatus("LIVE SESSION CLOSED")
                } else {
                    onStatus("LIVE CLOSED BEFORE SETUP • CODE $code " + reason.take(60))
                }
            }
        })
    }

    private fun startAudio() {
        if (!running.get() || !setupCompleted.get()) return
        if (!audioStarted.compareAndSet(false, true)) return
        try {
            val minIn = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val minOut = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minIn <= 0 || minOut <= 0) { onStatus("PHONE AUDIO FORMAT NOT SUPPORTED"); stop(); return }
            val input = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, 6400))
            if (input.state != AudioRecord.STATE_INITIALIZED) { input.release(); onStatus("MICROPHONE INITIALIZATION FAILED"); stop(); return }
            val output = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minOut, 12000))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            if (output.state != AudioTrack.STATE_INITIALIZED) {
                output.release()
                input.stop()
                input.release()
                onStatus("SPEAKER AUDIO INITIALIZATION FAILED")
                stop()
                return
            }
            output.setVolume(1.0f)
            recorder = input
            player = output
            output.play()
            input.startRecording()
            onStatus("LIVE • SPEAK TO MYRA")
            micThread = Thread({
                val buffer = ByteArray(3200)
                while (running.get()) {
                    val n = input.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (n > 0 && running.get()) {
                        // When muted, send silence frames instead of the captured microphone samples.
                        val audioFrame = if (muted.get()) ByteArray(n) else buffer.copyOf(n)
                        val chunk = Base64.encodeToString(audioFrame, Base64.NO_WRAP)
                        val message = JSONObject().put("realtimeInput", JSONObject().put("audio",
                            JSONObject().put("mimeType", "audio/pcm;rate=16000").put("data", chunk)))
                        if (socket?.send(message.toString()) != true) break
                    }
                }
            }, "Myra-Live-Mic").also { it.start() }
        } catch (_: SecurityException) {
            onStatus("MICROPHONE PERMISSION NEEDED"); stop()
        } catch (_: Exception) {
            onStatus("COULDN'T START LIVE AUDIO"); stop()
        }
    }

    fun stop() {
        running.set(false)
        muted.set(false)
        setupCompleted.set(false)
        try { micThread?.interrupt() } catch (_: Exception) {}
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { player?.pause(); player?.flush(); player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try { socket?.close(1000, "Session stopped") } catch (_: Exception) {}
        socket = null
    }

    private fun stopAudio() {
        running.set(false)
        audioStarted.set(false)
        try { micThread?.interrupt() } catch (_: Exception) {}
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { player?.pause(); player?.flush(); player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
    }
}
