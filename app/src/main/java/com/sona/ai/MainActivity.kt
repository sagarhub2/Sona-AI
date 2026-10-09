package com.sona.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Night = Color(0xFF050816)
private val Violet = Color(0xFF8B5CF6)
private val Cyan = Color(0xFF67E8F9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SonaHome() }
    }
}

@Composable
private fun SonaHome() {
    Surface(modifier = Modifier.fillMaxSize(), color = Night) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                modifier = Modifier
                    .size(220.dp)
                    .background(Brush.radialGradient(listOf(Color(0x558B5CF6), Color(0x2238BDF8), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(Brush.radialGradient(listOf(Color(0xFFB8A3FF), Violet, Color(0xFF243A83))), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .background(Brush.radialGradient(listOf(Color(0xFF111B43), Color(0xFF070B1D))), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("S", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("I’m Sona.", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Your personal AI companion", color = Color(0xFFB7C1DF), fontSize = 15.sp)
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) {
                Text("Start voice assistant", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
            Text("PHASE 1 • UI FOUNDATION", color = Color(0xFF66708F), fontSize = 10.sp, letterSpacing = 1.5.sp)
        }
    }
}

@Composable
private fun QuickTile(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(88.dp)
            .background(Color(0xFF151A31), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Color(0xFF9AA6C8), fontSize = 10.sp)
    }
}
