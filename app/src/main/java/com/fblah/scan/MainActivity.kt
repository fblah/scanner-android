package com.fblah.scan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ScanTheme { Library() } }
    }
}

@Composable
private fun ScanTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFF83D5C2), background = Color(0xFF101916),
        surface = Color(0xFF17231F), surfaceVariant = Color(0xFF253A32)
    ) else lightColorScheme(
        primary = Color(0xFF0D7566), background = Color(0xFFF6F8F4),
        surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE4EEE7),
        onBackground = Color(0xFF182C25), onSurface = Color(0xFF182C25)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun Library() {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = colors.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0D7566)) {
                    Icon(painterResource(R.drawable.ic_scan), contentDescription = null,
                        modifier = Modifier.size(52.dp), tint = Color.Unspecified)
                }
                Column {
                    Text("Scan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("A little less paper.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Make room for what matters.", color = colors.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(28.dp), color = colors.surface) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(120.dp).background(colors.surfaceVariant, RoundedCornerShape(36.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_scan), contentDescription = null,
                            modifier = Modifier.size(104.dp), tint = colors.primary)
                    }
                    Text("A fresh start", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Receipts, notes, and important pages.\nSoon, all together in one quiet place.",
                        style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceVariant) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Built for your device", fontWeight = FontWeight.SemiBold)
                    Text("Document processing and storage stay on your phone.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("EARLY PREVIEW", style = MaterialTheme.typography.labelMedium, color = colors.primary)
            Text("Scanning and importing are being built. Your library will appear here when they’re ready.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
