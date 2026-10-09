package com.sona.ai

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
    private val running = AtomicBoolean(false)
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var player: AudioTrack? = null
    @Volatile private var micThread: Thread? = null

    fun start() {
        if (apiKey.isBlank()) { onStatus("ADD GEMINI KEY IN SETTINGS"); return }
        if (!running.compareAndSet(false, true)) { onStatus("LIVE VOICE IS ALREADY RUNNING"); return }
        onStatus("CONNECTING TO GEMINI LIVE…")
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + URLEncoder.encode(apiKey, "UTF-8")
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                val setup = JSONObject().put("setup", JSONObject()
                    .put("model", "models/$model")
                    .put("generationConfig", JSONObject()
                        .put("responseModalities", JSONArray().put("AUDIO"))
                        .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", "Aoede")))))
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text",
                        "You are Sona, a warm and natural female AI companion. Talk like a kind Indian friend. Understand and reply naturally in Hindi or Hinglish. Keep spoken answers conversational and concise."))))
                    .put("inputAudioTranscription", JSONObject())
                    .put("outputAudioTranscription", JSONObject()))
                ws.send(setup.toString())
                onStatus("CONNECTED • SETTING UP VOICE…")
            }
            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    if (root.has("error")) {
                        val error = root.optJSONObject("error")
                        onStatus("GEMINI ERROR: " + (error?.optString("message") ?: "Unknown setup error").take(140))
                        stopAudio()
                        return
                    }
                    if (root.has("setupComplete")) startAudio()
                    val server = root.optJSONObject("serverContent")
                    server?.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotBlank() && it != "null" }?.let { onTranscript("You: $it") }
                    server?.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotBlank() && it != "null" }?.let(onTranscript)
                    val parts = server?.optJSONObject("modelTurn")?.optJSONArray("parts")
                    if (parts != null) for (i in 0 until parts.length()) {
                        val data = parts.optJSONObject(i)?.optJSONObject("inlineData")?.optString("data")
                        if (!data.isNullOrBlank()) {
                            val bytes = Base64.decode(data, Base64.DEFAULT)
                            player?.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING)
                        }
                    }
                    if (server?.optBoolean("interrupted", false) == true) { player?.pause(); player?.flush(); player?.play() }
                } catch (_: Exception) { onStatus("LIVE RESPONSE PROCESSING ERROR") }
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                stopAudio()
                onStatus("LIVE CONNECTION FAILED • CHECK KEY, MODEL OR INTERNET")
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                stopAudio()
                onStatus("LIVE SESSION CLOSED")
            }
        })
    }

    private fun startAudio() {
        try {
            val minIn = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val minOut = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minIn <= 0 || minOut <= 0) { onStatus("PHONE AUDIO FORMAT NOT SUPPORTED"); stop(); return }
            val input = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minIn, 6400))
            if (input.state != AudioRecord.STATE_INITIALIZED) { input.release(); onStatus("MICROPHONE INITIALIZATION FAILED"); stop(); return }
            val output = AudioTrack.Builder()
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minOut, 12000))
                .setTransferMode(AudioTrack.MODE_STREAM).build()
            recorder = input
            player = output
            output.play()
            input.startRecording()
            onStatus("LIVE • SPEAK TO SONA")
            micThread = Thread({
                val buffer = ByteArray(3200)
                while (running.get()) {
                    val n = input.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (n > 0 && running.get()) {
                        val chunk = Base64.encodeToString(buffer.copyOf(n), Base64.NO_WRAP)
                        val message = JSONObject().put("realtimeInput", JSONObject().put("audio",
                            JSONObject().put("mimeType", "audio/pcm;rate=16000").put("data", chunk)))
                        if (socket?.send(message.toString()) != true) break
                    }
                }
            }, "Sona-Live-Mic").also { it.start() }
        } catch (_: SecurityException) {
            onStatus("MICROPHONE PERMISSION NEEDED"); stop()
        } catch (_: Exception) {
            onStatus("COULDN'T START LIVE AUDIO"); stop()
        }
    }

    fun stop() {
        running.set(false)
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
        try { micThread?.interrupt() } catch (_: Exception) {}
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { player?.pause(); player?.flush(); player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
    }
}
