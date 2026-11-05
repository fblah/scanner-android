package com.fblah.scan

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val store = remember { DocumentStore(context) }
    var documents by remember { mutableStateOf(emptyList<DocumentStore.Document>()) }
    var importMessage by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { documents = withContext(Dispatchers.IO) { store.load() } }
    val importScope = rememberCoroutineScope()
    val importImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importScope.launch {
            importing = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val doc = store.import(uris)
                    val next = listOf(doc) + store.load()
                    store.save(next)
                    next
                }
            }.onSuccess { documents = it; importMessage = "Images saved on this device." }
                .onFailure { importMessage = "Import failed: ${it.localizedMessage}" }
            importing = false
        }
    }
    val updater = remember { AppUpdater(context) }
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<AppUpdater.Update?>(null) }
    var updateMessage by remember { mutableStateOf("Checking for updates…") }
    LaunchedEffect(Unit) {
        update = withContext(Dispatchers.IO) { runCatching { updater.check() }.getOrNull() }
        updateMessage = if (update == null) "No update available, or GitHub could not be reached." else "A new build is ready."
    }
    var dismissed by remember { mutableStateOf(false) }
    if (update != null && !dismissed) {
        AlertDialog(onDismissRequest = { dismissed = true },
            title = { Text("Update Scan") },
            text = { Text("A newer version is available. Download it now? Android will ask you to confirm installation.") },
            confirmButton = { TextButton(onClick = {
                dismissed = true
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { updater.download(update!!) } }
                        .onSuccess { updateMessage = "Downloading. Use App updates when finished to install."; update = null }
                        .onFailure { updateMessage = "Download failed. Please try again." }
                }
            }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { dismissed = true }) { Text("Later") } })
    }
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
            Button(onClick = { importImages.launch(arrayOf("image/*")) }, enabled = !importing,
                modifier = Modifier.fillMaxWidth()) { Text(if (importing) "Importing…" else "Import images") }
            if (importMessage.isNotEmpty()) Text(importMessage)
            documents.forEach { doc ->
                Surface(shape = RoundedCornerShape(20.dp), color = colors.surface) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(doc.name, style = MaterialTheme.typography.titleMedium)
                        Text("${doc.pages.size} pages · Saved on device", color = colors.onSurfaceVariant)
                    }
                }
            }
            if (documents.isEmpty()) Surface(shape = RoundedCornerShape(28.dp), color = colors.surface) {
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
            OutlinedButton(onClick = {
                scope.launch {
                    runCatching {
                        if (updater.installDownloaded()) return@runCatching
                        val available = update
                        if (available != null) {
                            withContext(Dispatchers.IO) { updater.download(available) }
                            updateMessage = "Downloading. Tap again when the download finishes to install."
                            update = null
                        } else {
                            update = withContext(Dispatchers.IO) { updater.check() }
                            updateMessage = if (update == null) "No update available." else "Update available. Tap to download."
                        }
                    }.onFailure { updateMessage = "Update unavailable. Please try again." }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("App updates") }
            Text(updateMessage, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text("EARLY PREVIEW", style = MaterialTheme.typography.labelMedium, color = colors.primary)
            Text("Scanning and importing are being built. Your library will appear here when they’re ready.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
