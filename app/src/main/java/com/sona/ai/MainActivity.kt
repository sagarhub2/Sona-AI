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
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
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
    private var showMemory by mutableStateOf(false)
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
                onSettings = { showSettings = true },
                onMemory = { showMemory = true },
                onSearch = { query ->
                    try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")))) }
                    catch (_: Exception) { status = "NO BROWSER AVAILABLE" }
                },
                onFiles = {
                    try {
                        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*", "text/*"))
                        }, 411)
                    } catch (_: Exception) { status = "FILE PICKER UNAVAILABLE" }
                },
                onDeviceSettings = {
                    try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                    catch (_: Exception) { status = "SETTINGS UNAVAILABLE" }
                },
                onCamera = {
                    try { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) }
                    catch (_: Exception) { status = "CAMERA UNAVAILABLE" }
                },
                onContacts = {
                    try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("content://contacts/people/"))) }
                    catch (_: Exception) { status = "CONTACTS UNAVAILABLE" }
                },
                onMedia = {
                    try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))) }
                    catch (_: Exception) { status = "MEDIA APP UNAVAILABLE" }
                }
            )
            if (showMemory) {
                var memoryDraft by remember { mutableStateOf(getSharedPreferences("sona_private", MODE_PRIVATE).getString("memory_notes", "") ?: "") }
                AlertDialog(
                    onDismissRequest = { showMemory = false },
                    title = { Text("Sona Memory") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Save notes for Sona to remember on this phone.")
                            OutlinedTextField(value = memoryDraft, onValueChange = { memoryDraft = it }, label = { Text("Your notes") }, minLines = 4, maxLines = 8)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            getSharedPreferences("sona_private", MODE_PRIVATE).edit().putString("memory_notes", memoryDraft).apply()
                            heardText = if (memoryDraft.isBlank()) "Memory cleared." else "Memory saved on this device."
                            status = "MEMORY UPDATED"
                            showMemory = false
                        }) { Text("Save memory") }
                    },
                    dismissButton = { TextButton(onClick = { showMemory = false }) { Text("Cancel") } }
                )
            }
            if (showSettings) {
                val prefs = getSharedPreferences("sona_private", MODE_PRIVATE)
                var keyDraft by remember { mutableStateOf(apiKey) }
                var languageDraft by remember { mutableStateOf(prefs.getString("language", "Auto (match me)") ?: "Auto (match me)") }
                var voiceDraft by remember { mutableStateOf(prefs.getString("voice_style", "Warm & natural") ?: "Warm & natural") }
                var personalityDraft by remember { mutableStateOf(prefs.getString("personality", "Friendly, helpful, concise") ?: "Friendly, helpful, concise") }
                var speakReplies by remember { mutableStateOf(prefs.getBoolean("speak_replies", true)) }
                var animatedOrb by remember { mutableStateOf(prefs.getBoolean("animated_orb", true)) }
                var rememberNotes by remember { mutableStateOf(prefs.getBoolean("use_memory", true)) }
                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = { Text("SONA AI • ALL SETTINGS", color = Cyan, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.heightIn(max = 480.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("AI PROVIDER & MODEL", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Provider: Google Gemini", fontSize = 13.sp)
                            Text("Voice model: Gemini Live (configured in this build)", fontSize = 11.sp, color = Color(0xFF9AA6C8))
                            OutlinedTextField(
                                value = keyDraft, onValueChange = { keyDraft = it },
                                label = { Text("Gemini API key") }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("VOICE & LANGUAGE", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            OutlinedTextField(value = languageDraft, onValueChange = { languageDraft = it }, label = { Text("Language (e.g. Hindi, English, Auto)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = voiceDraft, onValueChange = { voiceDraft = it }, label = { Text("Voice style preference") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            SettingToggle("Speak text replies aloud", speakReplies, { speakReplies = it })
                            Text("PERSONALITY & MEMORY", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            OutlinedTextField(value = personalityDraft, onValueChange = { personalityDraft = it }, label = { Text("Assistant personality / instructions") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                            SettingToggle("Use saved Memory notes in replies", rememberNotes, { rememberNotes = it })
                            Text("APPEARANCE", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            SettingToggle("Animated holographic orb", animatedOrb, { animatedOrb = it })
                            Text("PHONE, FILES & PERMISSIONS", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Camera, microphone, contacts, files, notifications and device settings use Android permission/system screens when required.", fontSize = 12.sp, color = Color(0xFF9AA6C8))
                            Text("WEB & SEARCH", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Web search opens your browser. Search history is not stored by this settings panel.", fontSize = 12.sp, color = Color(0xFF9AA6C8))
                            Text("PRIVACY & DIAGNOSTICS", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("API key and preferences are stored in this app's private local preferences. Never share your API key.", fontSize = 12.sp, color = Color(0xFF9AA6C8))
                            Text("Build: Sona AI • Android • Gemini", fontSize = 11.sp, color = Color(0xFF9AA6C8))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            apiKey = keyDraft.trim()
                            prefs.edit()
                                .putString("gemini_key", apiKey)
                                .putString("language", languageDraft.trim())
                                .putString("voice_style", voiceDraft.trim())
                                .putString("personality", personalityDraft.trim())
                                .putBoolean("speak_replies", speakReplies)
                                .putBoolean("animated_orb", animatedOrb)
                                .putBoolean("use_memory", rememberNotes)
                                .apply()
                            showSettings = false
                            heardText = if (apiKey.isBlank()) "API key removed." else "Settings saved • Gemini key stored"
                            status = if (apiKey.isBlank()) "AI NOT CONNECTED" else "SETTINGS SAVED • READY TO CONNECT"
                        }) { Text("Save all") }
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
                    if (message.startsWith("SPEAK_FALLBACK:")) {
                        val fallbackText = message.removePrefix("SPEAK_FALLBACK:").trim()
                        if (fallbackText.isNotBlank()) {
                            heardText = fallbackText
                            if (ttsReady) textToSpeech?.speak(fallbackText, TextToSpeech.QUEUE_FLUSH, null, "sona-live-fallback")
                        }
                    } else {
                        heardText = message
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
                val savedMemory = getSharedPreferences("sona_private", MODE_PRIVATE).getString("memory_notes", "").orEmpty().take(4000)
                val companionPrompt = "You are Sona, a warm, natural, friendly AI companion with a feminine voice. Speak like a kind Indian friend, not a robot. Reply in the user's language, especially natural Hindi or Hinglish when they use it. Keep spoken answers conversational and easy to say aloud; avoid markdown, lists, emojis, and overly long replies unless requested. Be respectful and supportive.\n\nUser's saved notes (use only when relevant):\n$savedMemory\n\nUser says: $prompt"
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
    onSettings: () -> Unit,
    onMemory: () -> Unit,
    onSearch: (String) -> Unit,
    onFiles: () -> Unit,
    onDeviceSettings: () -> Unit,
    onCamera: () -> Unit,
    onContacts: () -> Unit,
    onMedia: () -> Unit
) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "sona-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "orb-pulse"
    )
    val scrollState = androidx.compose.foundation.rememberScrollState()
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF10132D), Color(0xFF030611), Color(0xFF080B18))))
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("SONA AI", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(if (hasKey) Color(0xFF4ADE80) else Color(0xFFFBBF24), CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(if (hasKey) "AI READY TO CONNECT" else "YOUR PERSONAL AI", color = Cyan, fontSize = 10.sp, letterSpacing = 1.5.sp)
                    }
                }
                TextButton(onClick = onSettings) {
                    Text("⚙", color = Cyan, fontSize = 23.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier.size((220 * pulse).dp)
                    .background(Brush.radialGradient(listOf(Color(0x668B5CF6), Color(0x2238BDF8), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(174.dp).background(Brush.radialGradient(listOf(Color(0xFF9F67FF), Color(0xFF4626B5), Color(0xFF111B43))), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(150.dp).background(Brush.radialGradient(listOf(Color(0xFF111B43), Color(0xFF050816))), CircleShape), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✦", color = Cyan, fontSize = 28.sp)
                            Text("SONA", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp)
                            Text("AI ORB", color = Color(0xFFBCA7FF), fontSize = 9.sp, letterSpacing = 2.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Hey, welcome back ✨", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Your AI companion is here for you.", color = Color(0xFFADB8D8), fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10162B))
            ) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(if (hasKey) Color(0xFF4ADE80) else Color(0xFFFBBF24), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(status, color = if (hasKey) Color(0xFF86EFAC) else Color(0xFFFDE68A), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(heardText, color = Color(0xFFD2D9F0), fontSize = 13.sp, lineHeight = 19.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStartVoice,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673DE6))
            ) {
                Text(if (busy) "✦  Sona is thinking…" else "🎙   Talk to Sona", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Tap to start a voice conversation", color = Color(0xFF7784AA), fontSize = 11.sp)

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("QUICK ACCESS", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.weight(1f))
                Text("YOUR AI SPACE", color = Color(0xFF8B7CFF), fontSize = 9.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("🧠 Memory", "Notes & recall", Modifier.weight(1f), onClick = onMemory)
                QuickTile("🌐 Search", "Explore the web", Modifier.weight(1f), onClick = { showSearchDialog = true })
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("📱 Phone Control", "Device settings", Modifier.weight(1f), onClick = onDeviceSettings)
                QuickTile("🎵 Media", "Open YouTube", Modifier.weight(1f), onClick = onMedia)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("📷 Camera", "Open camera", Modifier.weight(1f), onClick = onCamera)
                QuickTile("📇 Contacts", "Open contacts", Modifier.weight(1f), onClick = onContacts)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("🗂 Files & Photos", "PDFs, images & documents", Modifier.weight(1f), onClick = onFiles)
                QuickTile("⚙ Settings", "API & preferences", Modifier.weight(1f), onClick = onSettings)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("📝 Notes & Tasks", "Save notes with Memory", Modifier.weight(1f), onClick = onMemory)
                QuickTile("🪄 Wallpaper", "Open display settings", Modifier.weight(1f), onClick = onDeviceSettings)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("👁 Camera & Vision", "Open camera", Modifier.weight(1f), onClick = onCamera)
                QuickTile("📞 Calls & Contacts", "Open contacts", Modifier.weight(1f), onClick = onContacts)
            }
            Spacer(Modifier.height(20.dp))
            Text("VOICE • MEMORY • SEARCH • TOOLS", color = Color(0xFF66708F), fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search the web") },
            text = { OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("What do you want to find?") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val query = searchQuery.trim()
                    if (query.isNotBlank()) onSearch(query)
                    showSearchDialog = false
                }) { Text("Search") }
            },
            dismissButton = { TextButton(onClick = { showSearchDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(17.dp)
    Surface(
        modifier = modifier.height(94.dp),
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = shape,
        color = Color(0xFF10172E),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF29335B))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(13.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = Color(0xFF9AA6C8), fontSize = 11.sp)
        }
    }
}
