package com.sona.ai

import android.Manifest
import android.content.Intent
import android.provider.AlarmClock
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
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.rotate
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
    private var showTasks by mutableStateOf(false)
    private var showHistory by mutableStateOf(false)
    private var showDeviceInfo by mutableStateOf(false)
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
                animatedOrb = getSharedPreferences("sona_private", MODE_PRIVATE).getBoolean("animated_orb", true),
                onStartVoice = { requestOrStartVoice() },
                onSettings = { showSettings = true },
                onMemory = { showMemory = true },
                onTasks = { showTasks = true },
                onHistory = { showHistory = true },
                onDeviceInfo = { showDeviceInfo = true },
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
                },
                onReminder = {
                    try { startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply { putExtra(AlarmClock.EXTRA_SKIP_UI, false) }) }
                    catch (_: Exception) { status = "CLOCK APP UNAVAILABLE" }
                },
                onPermissions = {
                    try { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
                    catch (_: Exception) { status = "APP SETTINGS UNAVAILABLE" }
                },
                onShareAnswer = {
                    val textToShare = heardText.trim()
                    if (textToShare.isBlank()) {
                        status = "NO ANSWER TO SHARE YET"
                    } else {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, textToShare)
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share Sona's answer"))
                        } catch (_: Exception) { status = "SHARING UNAVAILABLE" }
                    }
                }
,
                onSpeakAnswer = {
                    val speech = heardText.trim()
                    if (speech.isBlank()) {
                        status = "NO ANSWER TO SPEAK YET"
                    } else if (!ttsReady) {
                        status = "TEXT-TO-SPEECH IS NOT READY"
                    } else {
                        textToSpeech?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "sona-latest-answer")
                        status = "SPEAKING LATEST ANSWER"
                    }
                },
                onStopSpeaking = {
                    textToSpeech?.stop()
                    status = "SPEECH STOPPED"
                },
                onCopyAnswer = {
                    val textToCopy = heardText.trim()
                    if (textToCopy.isBlank()) {
                        status = "NO ANSWER TO COPY YET"
                    } else {
                        val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sona AI answer", textToCopy))
                        status = "ANSWER COPIED TO CLIPBOARD"
                    }
                }
,
                onAskText = { prompt -> askGemini(prompt) }
            )
            if (showDeviceInfo) {
                val batteryIntent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
                val plugged = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
                AlertDialog(
                    onDismissRequest = { showDeviceInfo = false },
                    title = { Text("Device Info") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
                            Text("Android: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")")
                            Text("Battery: " + (if (percent >= 0) percent.toString() + "%" else "Unavailable"))
                            Text(if (plugged != 0) "Power: Charging / connected" else "Power: Not charging")
                            Text("Sona AI • " + packageName, fontSize = 11.sp, color = Color(0xFF9AA6C8))
                        }
                    },
                    confirmButton = { TextButton(onClick = { showDeviceInfo = false }) { Text("Done") } }
                )
            }
            if (showHistory) {
                val prefs = getSharedPreferences("sona_private", MODE_PRIVATE)
                val savedHistory = prefs.getString("conversation_history", "").orEmpty()
                AlertDialog(
                    onDismissRequest = { showHistory = false },
                    title = { Text("Recent Conversations") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (savedHistory.isBlank()) "No saved conversation history yet. Ask Sona a question to start." else savedHistory,
                                color = Color(0xFFD2D9F0),
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showHistory = false }) { Text("Close") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            prefs.edit().remove("conversation_history").apply()
                            status = "CONVERSATION HISTORY CLEARED"
                            heardText = "Conversation history cleared on this device."
                            showHistory = false
                        }) { Text("Clear history") }
                    }
                )
            }
            if (showTasks) {
                val prefs = getSharedPreferences("sona_private", MODE_PRIVATE)
                var taskDraft by remember { mutableStateOf(prefs.getString("sona_tasks", "").orEmpty()) }
                AlertDialog(
                    onDismissRequest = { showTasks = false },
                    title = { Text("Sona Tasks") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("One task per line. Your list stays saved on this phone.")
                            OutlinedTextField(
                                value = taskDraft,
                                onValueChange = { taskDraft = it },
                                label = { Text("My tasks") },
                                placeholder = { Text("Finish homework\nDrink water\nReview my plans") },
                                minLines = 4,
                                maxLines = 8
                            )
                            Text("Tip: add [x] before a task when you finish it.", color = Color(0xFF7784AA), fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            prefs.edit().putString("sona_tasks", taskDraft).apply()
                            heardText = if (taskDraft.isBlank()) "Task list cleared." else "Tasks saved on this device."
                            status = "TASKS UPDATED"
                            showTasks = false
                        }) { Text("Save tasks") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            taskDraft = ""
                            prefs.edit().putString("sona_tasks", "").apply()
                            status = "TASKS CLEARED"
                            showTasks = false
                        }) { Text("Clear all") }
                    }
                )
            }
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
                var userNameDraft by remember { mutableStateOf(prefs.getString("user_name", "").orEmpty()) }
                var assistantNameDraft by remember { mutableStateOf(prefs.getString("assistant_name", "Sona").orEmpty()) }
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
                            Text("ASSISTANT PROFILE", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            OutlinedTextField(value = assistantNameDraft, onValueChange = { assistantNameDraft = it }, label = { Text("Assistant name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = userNameDraft, onValueChange = { userNameDraft = it }, label = { Text("What should Sona call you?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                            Text("Use Android app settings to review or change permissions available to Sona AI.", fontSize = 12.sp, color = Color(0xFF9AA6C8))
                            TextButton(onClick = {
                                try { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
                                catch (_: Exception) { status = "APP SETTINGS UNAVAILABLE" }
                            }) { Text("Open permission dashboard", color = Cyan) }
                            Text("WEB & SEARCH", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Web search opens your browser. Search history is not stored by this settings panel.", fontSize = 12.sp, color = Color(0xFF9AA6C8))
                            Text("PRIVACY & DIAGNOSTICS", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("API key and preferences are stored in this app's private local preferences. Never share your API key.", fontSize = 12.sp, color = Color(0xFF9AA6C8))
                            Text("CONVERSATION HISTORY", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Recent turns are saved locally to provide context to future replies.", fontSize = 12.sp, color = Color(0xFF9AA6C8))
                            TextButton(onClick = {
                                prefs.edit().remove("conversation_history").apply()
                                heardText = "Conversation history cleared on this device."
                                status = "HISTORY CLEARED"
                            }) { Text("Clear conversation history", color = Cyan) }
                            Text("DIAGNOSTICS", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Status: $status\\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\\nAndroid: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})\\nGemini key: ${if (apiKey.isBlank()) "not configured" else "saved"}", fontSize = 12.sp, color = Color(0xFF9AA6C8))
                            TextButton(onClick = { heardText = "Diagnostics refreshed • $status • Android ${android.os.Build.VERSION.RELEASE}" }) { Text("Refresh diagnostics", color = Cyan) }
                            Text("Build: Sona AI • Android • Gemini", fontSize = 11.sp, color = Color(0xFF9AA6C8))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            apiKey = keyDraft.trim()
                            prefs.edit()
                                .putString("gemini_key", apiKey)
                                .putString("user_name", userNameDraft.trim())
                                .putString("assistant_name", assistantNameDraft.trim().ifBlank { "Sona" })
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

    @Deprecated("Use Activity Result APIs when modernizing this screen")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 411 || resultCode != RESULT_OK) return
        val uri = data?.data ?: run { status = "NO FILE SELECTED"; return }
        val key = apiKey
        if (key.isBlank()) {
            status = "ADD GEMINI KEY IN SETTINGS"
            showSettings = true
            return
        }
        busy = true
        status = "READING FILE FOR GEMINI…"
        Thread {
            var answer: String? = null
            var error = "Could not read this file. Try a PDF, image, or text file."
            try {
                val mime = contentResolver.getType(uri) ?: "application/octet-stream"
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("File could not be opened")
                if (bytes.size > 15 * 1024 * 1024) throw IllegalArgumentException("File is over 15 MB. Choose a smaller file.")
                val parts = JSONArray()
                parts.put(JSONObject().put("text", "Analyze the attached file for the user. If it is a PDF, summarize its key points. If it is an image, describe what is visible. If it is text, summarize it. Answer in the user's language, clearly and accurately."))
                if (mime.startsWith("text/") || mime == "application/json") {
                    parts.put(JSONObject().put("text", String(bytes, Charsets.UTF_8).take(30000)))
                } else if (mime == "application/pdf" || mime.startsWith("image/")) {
                    parts.put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", mime)
                        .put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))))
                } else {
                    throw IllegalArgumentException("Unsupported file type: $mime. Select a PDF, image, or text file.")
                }
                val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                    .put("generationConfig", JSONObject().put("maxOutputTokens", 700))
                val encodedKey = URLEncoder.encode(key, "UTF-8")
                val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$encodedKey").openConnection() as HttpURLConnection)
                try {
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 20000
                    conn.readTimeout = 60000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code in 200..299) {
                        answer = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                            ?.optString("text")?.takeIf { it.isNotBlank() && it != "null" }
                        if (answer == null) error = "Gemini returned no file analysis."
                    } else {
                        error = when (code) {
                            400, 403 -> "Gemini rejected the request. Check your API key and enabled API."
                            413 -> "File is too large for this request. Choose a smaller file."
                            429 -> "Gemini usage limit reached. Try again later."
                            else -> "File analysis failed (HTTP $code)."
                        }
                    }
                } finally { conn.disconnect() }
            } catch (e: Exception) {
                error = e.message?.takeIf { it.isNotBlank() } ?: error
            }
            runOnUiThread {
                busy = false
                if (answer != null) {
                    heardText = answer!!
                    status = "FILE ANALYZED • GEMINI RESPONSE RECEIVED"
                    if (ttsReady && getSharedPreferences("sona_private", MODE_PRIVATE).getBoolean("speak_replies", true))
                        textToSpeech?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "sona-file-analysis")
                } else {
                    heardText = error
                    status = "FILE ANALYSIS FAILED"
                }
            }
        }.start()
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
                val prefs = getSharedPreferences("sona_private", MODE_PRIVATE)
                val savedMemory = if (prefs.getBoolean("use_memory", true)) prefs.getString("memory_notes", "").orEmpty().take(4000) else ""
                val languagePreference = prefs.getString("language", "Auto (match me)").orEmpty().trim().ifBlank { "Auto (match me)" }
                val voicePreference = prefs.getString("voice_style", "Warm & natural").orEmpty().trim().ifBlank { "Warm & natural" }
                val personalityPreference = prefs.getString("personality", "Friendly, helpful, concise").orEmpty().trim().ifBlank { "Friendly, helpful, concise" }
                val conversationHistory = prefs.getString("conversation_history", "").orEmpty().takeLast(6000)
                val userName = prefs.getString("user_name", "").orEmpty().trim()
                val assistantName = prefs.getString("assistant_name", "Sona").orEmpty().ifBlank { "Sona" }
                val companionPrompt = "You are $assistantName, a helpful AI assistant. Address the user as $userName when their name is provided. Personality and response style requested by the user: $personalityPreference. Voice style preference: $voicePreference. Language preference: $languagePreference. If language is Auto (match me), reply in the language the user used, especially natural Hindi/Hinglish when appropriate. Make answers sound natural when spoken aloud. Keep replies concise unless asked for detail, and avoid markdown when a short spoken answer is enough. Respect user privacy and be honest about actions you cannot perform.\n\nUser's saved notes (use only when relevant):\n$savedMemory\n\nRecent conversation history (for continuity):\n$conversationHistory\n\nUser says: $prompt"
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
                    val prefs = getSharedPreferences("sona_private", MODE_PRIVATE)
                    val previousHistory = prefs.getString("conversation_history", "").orEmpty()
                    val updatedHistory = (previousHistory + "\\nUser: " + prompt + "\\nSona: " + responseText).takeLast(12000)
                    prefs.edit().putString("conversation_history", updatedHistory).apply()
                    status = "GEMINI CONNECTED • RESPONSE RECEIVED"
                    if (ttsReady && getSharedPreferences("sona_private", MODE_PRIVATE).getBoolean("speak_replies", true)) textToSpeech?.speak(responseText, TextToSpeech.QUEUE_FLUSH, null, "sona-gemini-reply")
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
    animatedOrb: Boolean,
    onStartVoice: () -> Unit,
    onSettings: () -> Unit,
    onMemory: () -> Unit,
    onTasks: () -> Unit,
    onHistory: () -> Unit,
    onDeviceInfo: () -> Unit,
    onSearch: (String) -> Unit,
    onFiles: () -> Unit,
    onDeviceSettings: () -> Unit,
    onCamera: () -> Unit,
    onContacts: () -> Unit,
    onMedia: () -> Unit,
    onReminder: () -> Unit,
    onPermissions: () -> Unit,
    onShareAnswer: () -> Unit,
    onCopyAnswer: () -> Unit,
    onSpeakAnswer: () -> Unit,
    onStopSpeaking: () -> Unit,
    onAskText: (String) -> Unit
) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "sona-orb")
    val animatedPulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "orb-pulse"
    )
    val pulse = if (animatedOrb) animatedPulse else 1f
    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(9000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "orb-ring-rotation"
    )
    val counterRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(12000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "orb-counter-rotation"
    )
    val scrollState = androidx.compose.foundation.rememberScrollState()
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showTranslateDialog by remember { mutableStateOf(false) }
    var translationSource by remember { mutableStateOf("") }
    var translationLanguage by remember { mutableStateOf("Hindi") }
    var showStudyDialog by remember { mutableStateOf(false) }
    var studyTopic by remember { mutableStateOf("") }
    var studyMode by remember { mutableStateOf("Explain simply") }
    var showWritingStudio by remember { mutableStateOf(false) }
    var writingRequest by remember { mutableStateOf("") }
    var writingMode by remember { mutableStateOf("Professional message") }
    var showPlannerDialog by remember { mutableStateOf(false) }
    var plannerGoal by remember { mutableStateOf("") }
    var plannerDuration by remember { mutableStateOf("7 days") }
    var plannerStyle by remember { mutableStateOf("Balanced daily plan") }
    var showDecisionDialog by remember { mutableStateOf(false) }
    var decisionQuestion by remember { mutableStateOf("") }
    var decisionOptions by remember { mutableStateOf("") }
    var decisionPriority by remember { mutableStateOf("Best long-term value") }
    var showJournalDialog by remember { mutableStateOf(false) }
    val journalKey = "sona_journal_" + java.time.LocalDate.now().toString()
    var journalEntry by remember { mutableStateOf(context.getSharedPreferences("sona_prefs", 0).getString(journalKey, "") ?: "") }
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
                modifier = Modifier.size(236.dp),
                contentAlignment = Alignment.Center
            ) {
                if (animatedOrb) {
                    Box(
                        Modifier.size(224.dp).rotate(ringRotation)
                            .border(1.5.dp, Brush.sweepGradient(listOf(Cyan, Color.Transparent, Violet, Color.Transparent, Cyan)), CircleShape)
                    )
                    Box(
                        Modifier.size(202.dp).rotate(counterRotation)
                            .border(1.dp, Brush.sweepGradient(listOf(Color.Transparent, Cyan, Color.Transparent, Violet, Color.Transparent)), CircleShape)
                    )
                    Box(
                        Modifier.size(184.dp).rotate(ringRotation * 0.55f)
                            .border(2.dp, Brush.sweepGradient(listOf(Color.Transparent, Color(0x668B5CF6), Cyan, Color.Transparent)), CircleShape)
                    )
                    Box(Modifier.size(224.dp).rotate(ringRotation), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.padding(top = 1.dp).size(10.dp).background(Cyan, CircleShape)
                            .border(2.dp, Color(0x6648E8FF), CircleShape))
                    }
                    Box(Modifier.size(202.dp).rotate(counterRotation), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.padding(bottom = 1.dp).size(7.dp).background(Violet, CircleShape)
                            .border(2.dp, Color(0x668B5CF6), CircleShape))
                    }
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.size(236.dp).rotate(ringRotation * 1.2f)
                    ) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color.Transparent, Cyan, Violet, Color.Transparent)),
                            startAngle = 215f,
                            sweepAngle = 72f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                            topLeft = androidx.compose.ui.geometry.Offset(5.dp.toPx(), 5.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(size.width - 10.dp.toPx(), size.height - 10.dp.toPx())
                        )
                    }
                }
                Box(
                    modifier = Modifier.size((176 * pulse).dp)
                        .background(Brush.radialGradient(listOf(Color(0x889F67FF), Color(0x554626B5), Color.Transparent)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(164.dp).background(Brush.radialGradient(listOf(Color(0xFF9F67FF), Color(0xFF4626B5), Color(0xFF111B43))), CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(142.dp).background(Brush.radialGradient(listOf(Color(0xFF111B43), Color(0xFF050816))), CircleShape), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✦", color = Cyan, fontSize = 28.sp)
                                Text("SONA", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp)
                                Text("AI ORB", color = Color(0xFFBCA7FF), fontSize = 9.sp, letterSpacing = 2.sp)
                            }
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
            var typedPrompt by remember { mutableStateOf("") }
            OutlinedTextField(
                value = typedPrompt,
                onValueChange = { typedPrompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ask Sona anything by typing") },
                placeholder = { Text("Write your question…") },
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = Color(0xFF29335B),
                    focusedLabelColor = Cyan,
                    unfocusedLabelColor = Color(0xFF9AA6C8),
                    cursorColor = Cyan
                ),
                maxLines = 4
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val prompt = typedPrompt.trim()
                    if (prompt.isNotBlank()) {
                        onAskText(prompt)
                        typedPrompt = ""
                    }
                },
                enabled = !busy && typedPrompt.isNotBlank() && hasKey,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E7490))
            ) {
                Text("➤  Send text question", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
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
                QuickTile("📝 Notes & Tasks", "Save notes with Memory", Modifier.weight(1f), onClick = onTasks)
                QuickTile("🪄 Wallpaper", "Open display settings", Modifier.weight(1f), onClick = onDeviceSettings)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("⏰ Alarms & Timers", "Create an alarm", Modifier.weight(1f), onClick = onReminder)
                QuickTile("🛡 Permissions", "App access controls", Modifier.weight(1f), onClick = onPermissions)
            }
            Spacer(Modifier.height(10.dp))
            QuickTile("🕘 Recent Conversations", "View or clear saved chat context", Modifier.fillMaxWidth(), onClick = onHistory)
            Spacer(Modifier.height(10.dp))
            QuickTile("📊 Device Info", "Battery, Android version & model", Modifier.fillMaxWidth(), onClick = onDeviceInfo)
            Spacer(Modifier.height(10.dp))
            QuickTile("🌍 Quick Translate", "Translate text with Sona AI", Modifier.fillMaxWidth(), onClick = { showTranslateDialog = true })
            Spacer(Modifier.height(10.dp))
            QuickTile("🎓 Study Mode", "Learn topics, quiz yourself, make notes", Modifier.fillMaxWidth(), onClick = { showStudyDialog = true })
            Spacer(Modifier.height(10.dp))
            QuickTile("✍️ Writing Studio", "Draft messages, captions, emails and ideas", Modifier.fillMaxWidth(), onClick = { showWritingStudio = true })
            Spacer(Modifier.height(10.dp))
            QuickTile("🗓️ Goal Planner", "Turn a goal into clear, manageable steps", Modifier.fillMaxWidth(), onClick = { showPlannerDialog = true })
            Spacer(Modifier.height(10.dp))
            QuickTile("⚖️ Decision Helper", "Compare options, trade-offs and next steps", Modifier.fillMaxWidth(), onClick = { showDecisionDialog = true })
            Spacer(Modifier.height(10.dp))
            QuickTile("📔 Daily Journal", "Write and save today's thoughts privately on this phone", Modifier.fillMaxWidth(), onClick = {
                journalEntry = context.getSharedPreferences("sona_prefs", 0).getString(journalKey, "") ?: ""
                showJournalDialog = true
            })
            Spacer(Modifier.height(10.dp))
            QuickTile("↗ Share latest answer", "Send Sona's reply to another app", Modifier.fillMaxWidth(), onClick = onShareAnswer)
            Spacer(Modifier.height(10.dp))
            QuickTile("📋 Copy latest answer", "Copy Sona's reply to clipboard", Modifier.fillMaxWidth(), onClick = onCopyAnswer)
            Spacer(Modifier.height(10.dp))
            QuickTile("🔊 Speak latest answer", "Read Sona's reply aloud", Modifier.fillMaxWidth(), onClick = onSpeakAnswer)
            Spacer(Modifier.height(10.dp))
            QuickTile("⏹ Stop speaking", "Stop voice playback immediately", Modifier.fillMaxWidth(), onClick = onStopSpeaking)
            Spacer(Modifier.height(20.dp))
            Text("VOICE • MEMORY • SEARCH • TOOLS", color = Color(0xFF66708F), fontSize = 10.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
        }
    }
    if (showStudyDialog) {
        AlertDialog(
            onDismissRequest = { showStudyDialog = false },
            title = { Text("Study Mode", color = Cyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose how Sona should help you learn.")
                    listOf("Explain simply", "Make revision notes", "Quiz me", "Give examples").forEach { mode ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = studyMode == mode, onClick = { studyMode = mode })
                            Text(mode, color = Color.White)
                        }
                    }
                    OutlinedTextField(
                        value = studyTopic,
                        onValueChange = { studyTopic = it },
                        label = { Text("Topic or question") },
                        placeholder = { Text("e.g. demand and supply") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val topic = studyTopic.trim()
                    if (topic.isNotBlank()) {
                        val instruction = when (studyMode) {
                            "Make revision notes" -> "Create clear, exam-friendly revision notes with headings and key points"
                            "Quiz me" -> "Act as a tutor and quiz me one question at a time. Ask the first question and wait for my answer"
                            "Give examples" -> "Teach this topic using simple, practical examples"
                            else -> "Explain this topic in simple language step by step, with a short example"
                        }
                        onAskText("$instruction about: $topic. Match the language I use and keep the response easy to understand.")
                        showStudyDialog = false
                        studyTopic = ""
                    }
                }) { Text("Start learning") }
            },
            dismissButton = { TextButton(onClick = { showStudyDialog = false }) { Text("Cancel") } }
        )
    }
    if (showWritingStudio) {
        AlertDialog(
            onDismissRequest = { showWritingStudio = false },
            title = { Text("Writing Studio", color = Cyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose a format and describe what you want to write.")
                    listOf("Professional message", "Email", "Instagram caption", "Application letter", "Creative ideas").forEach { mode ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = writingMode == mode, onClick = { writingMode = mode })
                            Text(mode, color = Color.White)
                        }
                    }
                    OutlinedTextField(
                        value = writingRequest,
                        onValueChange = { writingRequest = it },
                        label = { Text("What should Sona write?") },
                        placeholder = { Text("Add purpose, details and preferred tone…") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val request = writingRequest.trim()
                    if (request.isNotBlank()) {
                        onAskText("Writing Studio task: Create a $writingMode based on this brief: $request. Make it polished, natural, original, ready to edit, and match the language used in the brief. Return only the draft unless a brief clarification is essential.")
                        showWritingStudio = false
                        writingRequest = ""
                    }
                }) { Text("Create draft") }
            },
            dismissButton = { TextButton(onClick = { showWritingStudio = false }) { Text("Cancel") } }
        )
    }
    if (showPlannerDialog) {
        AlertDialog(
            onDismissRequest = { showPlannerDialog = false },
            title = { Text("AI Goal Planner", color = Cyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tell Sona what you want to accomplish.")
                    listOf("Balanced daily plan", "Quick starter steps", "Detailed weekly plan").forEach { style ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = plannerStyle == style, onClick = { plannerStyle = style })
                            Text(style, color = Color.White)
                        }
                    }
                    Text("Timeline", color = Cyan, fontWeight = FontWeight.Medium)
                    listOf("Today", "7 days", "30 days").forEach { duration ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = plannerDuration == duration, onClick = { plannerDuration = duration })
                            Text(duration, color = Color.White)
                        }
                    }
                    OutlinedTextField(
                        value = plannerGoal,
                        onValueChange = { plannerGoal = it },
                        label = { Text("Your goal") },
                        placeholder = { Text("e.g. learn React basics") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val goal = plannerGoal.trim()
                    if (goal.isNotBlank()) {
                        onAskText("Create a $plannerDuration plan for this goal: $goal. Plan style: $plannerStyle. Break it into realistic steps with priorities, time estimates, a simple checklist, and a progress review. Avoid unrealistic promises and ask no follow-up questions; state reasonable assumptions.")
                        showPlannerDialog = false
                        plannerGoal = ""
                    }
                }) { Text("Build my plan") }
            },
            dismissButton = { TextButton(onClick = { showPlannerDialog = false }) { Text("Cancel") } }
        )
    }
    if (showJournalDialog) {
        AlertDialog(
            onDismissRequest = { showJournalDialog = false },
            title = { Text("Daily Journal", color = Cyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today • " + java.time.LocalDate.now().toString(), color = Cyan)
                    Text("Write down what happened, what you learned, or what you want to remember.")
                    OutlinedTextField(
                        value = journalEntry,
                        onValueChange = { journalEntry = it },
                        placeholder = { Text("Dear journal…") },
                        minLines = 5,
                        maxLines = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Saved on this device only. This is not cloud-synced.", color = Color.Gray, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("sona_prefs", 0).edit().putString(journalKey, journalEntry).apply()
                    showJournalDialog = false
                    status = "TODAY'S JOURNAL SAVED"
                }) { Text("Save entry") }
            },
            dismissButton = {
                TextButton(onClick = {
                    journalEntry = ""
                    context.getSharedPreferences("sona_prefs", 0).edit().remove(journalKey).apply()
                    showJournalDialog = false
                    status = "TODAY'S JOURNAL CLEARED"
                }) { Text("Clear") }
            }
        )
    }
    if (showDecisionDialog) {
        AlertDialog(
            onDismissRequest = { showDecisionDialog = false },
            title = { Text("AI Decision Helper", color = Cyan, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sona will compare the options and explain the trade-offs.")
                    OutlinedTextField(
                        value = decisionQuestion,
                        onValueChange = { decisionQuestion = it },
                        label = { Text("What are you deciding?") },
                        placeholder = { Text("e.g. which project should I start?") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = decisionOptions,
                        onValueChange = { decisionOptions = it },
                        label = { Text("Options (one per line)") },
                        placeholder = { Text("Option A\nOption B\nOption C") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("What matters most?", color = Cyan)
                    listOf("Best long-term value", "Lowest cost", "Fastest result", "Simplest option").forEach { priority ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = decisionPriority == priority, onClick = { decisionPriority = priority })
                            Text(priority, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val question = decisionQuestion.trim()
                    val options = decisionOptions.trim()
                    if (question.isNotBlank() && options.isNotBlank()) {
                        onAskText("Help me make this decision: $question. Options:\n$options\nPriority: $decisionPriority. Compare pros and cons, note uncertainties, give a clear recommendation with reasons, and suggest one practical next step. Do not pretend to know facts not provided.")
                        showDecisionDialog = false
                        decisionQuestion = ""
                        decisionOptions = ""
                    }
                }) { Text("Compare options") }
            },
            dismissButton = { TextButton(onClick = { showDecisionDialog = false }) { Text("Cancel") } }
        )
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
private fun SettingToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color(0xFFD2D9F0), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
