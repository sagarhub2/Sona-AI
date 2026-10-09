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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val Night = Color(0xFF050816)
private val Violet = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF67E8F9)
private const val AUDIO_PERMISSION_REQUEST = 410

class MainActivity : ComponentActivity() {
    private var status by mutableStateOf("READY • TAP TO SPEAK")
    private var heardText by mutableStateOf("Try saying: Hello Sona")
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(this) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) textToSpeech?.language = Locale("en", "IN")
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { status = "LISTENING…" }
                    override fun onBeginningOfSpeech() { status = "HEARING YOU…" }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { status = "PROCESSING…" }
                    override fun onError(error: Int) {
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
                            heardText = "You said: $phrase"
                            status = "VOICE INPUT WORKED"
                            val reply = "I heard you say, $phrase. My AI connection is not set up yet."
                            if (ttsReady) textToSpeech?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "sona-reply")
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } else {
            status = "SPEECH RECOGNITION NOT AVAILABLE"
        }
        setContent {
            SonaHome(status = status, heardText = heardText, onStartVoice = { requestOrStartVoice() })
        }
    }

    private fun requestOrStartVoice() {
        if (speechRecognizer == null) {
            status = "SPEECH RECOGNITION NOT AVAILABLE ON THIS DEVICE"
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
            return
        }
        startVoiceRecognition()
    }

    private fun startVoiceRecognition() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Sona")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            status = "STARTING MICROPHONE…"
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            status = "COULDN’T START VOICE INPUT • TRY AGAIN"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition()
            } else {
                status = "MICROPHONE PERMISSION DENIED"
            }
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }
}

@Composable
private fun SonaHome(status: String, heardText: String, onStartVoice: () -> Unit) {
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
                Text("●  READY", color = Color(0xFF86EFAC), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(54.dp))
            Box(
                modifier = Modifier.size(220.dp).background(
                    Brush.radialGradient(listOf(Color(0x558B5CF6), Color(0x2238BDF8), Color.Transparent)), CircleShape
                ), contentAlignment = Alignment.Center
            ) {
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
            Text(status, color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            Text(heardText, color = Color(0xFFB7C1DF), fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onStartVoice, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                Text("🎙  Start voice assistant", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
            Text("PHASE 1 • VOICE PROTOTYPE", color = Color(0xFF66708F), fontSize = 10.sp, letterSpacing = 1.5.sp)
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
