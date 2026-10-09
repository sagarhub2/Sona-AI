package com.sona.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private val Night = Color(0xFF050816)
private val Violet = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF67E8F9)
private const val AUDIO_PERMISSION_REQUEST = 410

class MainActivity : ComponentActivity() {
    private var status by mutableStateOf("READY • TAP TO SPEAK")
    private var heardText by mutableStateOf("Add your Gemini API key in Settings to enable AI.")
    private var apiKey by mutableStateOf("")
    private var showSettings by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var speechRecognizer: SpeechRecognizer? = null
    private var liveVoiceSession: LiveVoiceSession? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apiKey = getSharedPreferences("sona_private", MODE_PRIVATE).getString("gemini_key", "") ?: ""
        heardText = if (apiKey.isBlank()) "Add your Gemini API key in Settings to enable AI." else "Gemini key saved • ready to connect"
        textToSpeech = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) {
                textToSpeech?.setSpeechRate(0.94f)
                textToSpeech?.setPitch(1.06f)
                val engine = textToSpeech
                val voices = engine?.voices.orEmpty()
                // Prefer a good-quality Hindi (India) voice; select a female-labelled voice
                // when the installed TTS engine exposes one. Voice availability varies by phone.
                val hindiVoices = voices.filter { it.locale.language == "hi" && it.locale.country == "IN" }
                val preferred = hindiVoices
                    .sortedWith(compareBy<android.speech.tts.Voice>(
                        { voice -> if (listOf("female", "woman", "feminine").any { voice.name.contains(it, true) }) 0 else 1 },
                        { voice -> if (voice.isNetworkConnectionRequired) 1 else 0 },
                        { voice -> -voice.quality }
                    ))
                    .firstOrNull()
                if (preferred != null) {
                    engine?.voice = preferred
                    engine?.language = preferred.locale
                } else {
                    engine?.language = Locale("hi", "IN")
                }
            }
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { status = "LISTENING…" }
                    override fun onBeginningOfSpeech() { status = "HEARING YOU…" }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { status = "ASKING GEMINI…" }
                    override fun onError(error: Int) {
                        busy = false
                        status = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "DIDN’T CATCH THAT • TRY AGAIN"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "VOICE SERVICE BUSY • TRY AGAIN"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "MICROPHONE PERMISSION NEEDED"
                            else -> "VOICE INPUT UNAVAILABLE • TRY AGAIN"
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (phrase.isNullOrBlank()) {
                            status = "NO SPEECH DETECTED"
                        } else {
                            heardText = "You: $phrase"
                            askGemini(phrase)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } else status = "SPEECH RECOGNITION NOT AVAILABLE"

        setContent {
            SonaHome(
                status = status,
                heardText = heardText,
                hasKey = apiKey.isNotBlank(),
                busy = busy,
                onStartVoice = { requestOrStartVoice() },
                onSettings = { showSettings = true }
            )
            if (showSettings) {
                var keyDraft by remember { mutableStateOf(apiKey) }
                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = { Text("Gemini AI connection") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Paste your Google AI Studio API key. Keep it private.")
                            OutlinedTextField(
                                value = keyDraft,
                                onValueChange = { keyDraft = it },
                                label = { Text("Gemini API key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation()
                            )
                            Text("The key is stored in this app's private preferences. For public release, use a secure backend proxy.", fontSize = 11.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            apiKey = keyDraft.trim()
                            getSharedPreferences("sona_private", MODE_PRIVATE).edit().putString("gemini_key", apiKey).apply()
                            showSettings = false
                            heardText = if (apiKey.isBlank()) "API key removed." else "API key saved • tap voice to test Gemini"
                            status = if (apiKey.isBlank()) "AI NOT CONNECTED" else "KEY SAVED • READY TO TEST"
                        }) { Text("Save key") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSettings = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }

    private fun requestOrStartVoice() {
        if (busy) return
        if (apiKey.isBlank()) {
            status = "ADD GEMINI KEY IN SETTINGS"
            showSettings = true
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
            return
        }
        startVoiceRecognition()
    }

    private fun startVoiceRecognition() {
        if (apiKey.isBlank()) {
            status = "ADD GEMINI KEY IN SETTINGS"
            showSettings = true
            return
        }
        // A previous Live session may have failed silently; always clean it up before retrying.
        liveVoiceSession?.stop()
        liveVoiceSession = null
        status = "STARTING GEMINI LIVE AUDIO…"
        liveVoiceSession = LiveVoiceSession(
            apiKey = apiKey,
            onStatus = { message -> runOnUiThread { status = message } },
            onTranscript = { message ->
                runOnUiThread {
                    heardText = message
                    // Fallback speech: if Live audio playback is silent but Gemini sends
                    // output transcription, speak the text with Android's installed TTS voice.
                    if (!message.startsWith("You:") && message.isNotBlank() && ttsReady) {
                        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "sona-live-transcript")
                    }
                }
            }
        )
        liveVoiceSession?.start()
    }

    private fun askGemini(prompt: String) {
        val key = apiKey
        if (key.isBlank()) {
            status = "ADD GEMINI KEY IN SETTINGS"
            return
        }
        busy = true
        status = "GEMINI IS THINKING…"
        Thread {
            var responseText: String? = null
            var errorText = "Could not connect to Gemini. Check internet, API key, and API quota."
            var connection: HttpURLConnection? = null
            try {
                val encodedKey = URLEncoder.encode(key, "UTF-8")
                connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$encodedKey").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val companionPrompt = "You are Sona, a warm, natural, friendly female AI companion. Speak like a kind Indian friend, not a robot. Reply in the user's language, especially natural Hindi or Hinglish when they use it. Keep spoken answers conversational and easy to say aloud; avoid markdown, lists, emojis, and overly long replies unless requested. Be respectful and supportive. User says: $prompt"
                val body = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", companionPrompt)))))
                    .put("generationConfig", JSONObject().put("maxOutputTokens", 300))
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code in 200..299) {
                    val root = JSONObject(raw)
                    responseText = root.optJSONArray("candidates")
                        ?.optJSONObject(0)?.optJSONObject("content")
                        ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                        ?.takeIf { !it.isNullOrBlank() && it != "null" }
                    if (responseText == null) errorText = "Gemini returned an empty answer. Try again."
                } else {
                    errorText = when (code) {
                        400, 403 -> "Gemini rejected the API key or request. Check the key and enabled API."
                        429 -> "Gemini usage limit reached. Check your AI Studio quota."
                        else -> "Gemini connection error (HTTP $code). Check your key and internet."
                    }
                }
            } catch (_: Exception) {
                errorText = "Connection failed. Check internet and confirm the Gemini API key is valid."
            } finally {
                connection?.disconnect()
            }
            runOnUiThread {
                busy = false
                if (responseText != null) {
                    heardText = responseText!!
                    status = "GEMINI CONNECTED • RESPONSE RECEIVED"
                    if (ttsReady) textToSpeech?.speak(responseText, TextToSpeech.QUEUE_FLUSH, null, "sona-gemini-reply")
                } else {
                    heardText = errorText
                    status = "AI CONNECTION FAILED"
                }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startVoiceRecognition()
            else status = "MICROPHONE PERMISSION DENIED"
        }
    }

    override fun onDestroy() {
        liveVoiceSession?.stop()
        liveVoiceSession = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }
}

@Composable
private fun SonaHome(
    status: String,
    heardText: String,
    hasKey: Boolean,
    busy: Boolean,
    onStartVoice: () -> Unit,
    onSettings: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF10132D), Night, Color(0xFF080B18))))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("SONA AI", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("PERSONAL AI ASSISTANT", color = Cyan, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                TextButton(onClick = onSettings) { Text("⚙ Settings", color = Cyan) }
            }
            Spacer(Modifier.height(42.dp))
            Box(modifier = Modifier.size(220.dp).background(
                Brush.radialGradient(listOf(Color(0x558B5CF6), Color(0x2238BDF8), Color.Transparent)), CircleShape
            ), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(150.dp).background(
                    Brush.radialGradient(listOf(Color(0xFFB8A3FF), Violet, Color(0xFF243A83))), CircleShape
                ), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(112.dp).background(
                        Brush.radialGradient(listOf(Color(0xFF111B43), Color(0xFF070B1D))), CircleShape
                    ), contentAlignment = Alignment.Center) {
                        Text("S", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("I’m Sona.", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Your personal AI companion", color = Color(0xFFB7C1DF), fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
            Text(status, color = if (hasKey) Color(0xFF86EFAC) else Cyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(heardText, color = Color(0xFFB7C1DF), fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onStartVoice, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                Text(if (busy) "Thinking…" else "🎙  Talk to Sona", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(22.dp))
            Text("QUICK ACCESS", color = Color(0xFF7F8AAE), fontSize = 11.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Memory", "Notes & recall", Modifier.weight(1f))
                QuickTile("Explore", "Web search", Modifier.weight(1f))
                QuickTile("Files", "PDF & images", Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Text("PHASE 1 • GEMINI VOICE PROTOTYPE", color = Color(0xFF66708F), fontSize = 10.sp, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.height(88.dp).background(Color(0xFF151A31), RoundedCornerShape(16.dp)).padding(10.dp),
        verticalArrangement = Arrangement.Center) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Color(0xFF9AA6C8), fontSize = 10.sp)
    }
}
