package com.fblah.scan

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = when(intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList() ?: emptyList()
            else -> emptyList()
        }
        setContent { MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary=Color(0xFF83D5C2)) else lightColorScheme(primary=Color(0xFF0D7566))) { ScanApp(incoming) } }
    }
}

@Composable
private fun ScanApp(incoming: List<Uri>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val analytics = remember { Analytics(context) }
    var analyticsEnabled by remember { mutableStateOf(analytics.enabled) }
    val store = remember { DocumentStore(context) }
    val scope = rememberCoroutineScope()
    var docs by remember { mutableStateOf(emptyList<DocumentStore.Document>()) }
    var active by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var selectedDocs by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saveRequested by remember { mutableStateOf(false) }
    var saveFile by remember { mutableStateOf<File?>(null) }
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val file=saveFile
        if(uri!=null && file!=null) scope.launch {runCatching {withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use {it.copyTo(output)} }}}.onFailure{message="Save failed"} }
    }
    var format by remember { mutableStateOf("Auto") }
    var review by remember { mutableStateOf<DocumentStore.Document?>(null) }
    var quality by remember { mutableStateOf("Standard") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val doc = docs.find { it.id == active }
    fun persist(next: List<DocumentStore.Document>) { store.save(next); docs = next; analytics.track("library_changed",next.size) }
    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val target = active
        scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { store.import(uris) } }.onSuccess { imported ->
                val existing = docs.find { it.id == target }
                persist(if(existing == null) listOf(imported)+docs else docs.map { if(it.id==target) it.copy(pages=it.pages+imported.pages) else it })
                analytics.track("pages_added",imported.pages.size)
                active = existing?.id ?: imported.id
            }.onFailure { message = "Import failed: ${it.localizedMessage}" }
            busy = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { add(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if(ok) cameraUri?.let { uri -> scope.launch {
            busy=true
            runCatching { withContext(Dispatchers.IO){store.import(listOf(uri))} }
                .onSuccess {review=it}.onFailure {message="Capture failed: ${it.localizedMessage}"}
            busy=false
        } }
    }
    fun capture() { val file=File.createTempFile("capture", ".jpg",context.cacheDir); cameraUri=FileProvider.getUriForFile(context,"${context.packageName}.files",file); camera.launch(cameraUri!!) }
    LaunchedEffect(Unit) { docs=withContext(Dispatchers.IO){store.load()}; analytics.track("pageview"); add(incoming) }
    BackHandler(active!=null) { active=null; selected=emptySet() }
    fun clonePage(path:String):String {
        val out=File(File(path).parentFile,"${java.util.UUID.randomUUID()}.jpg")
        File(path).copyTo(out)
        File(out.path+".source").writeText(Scanner.source(path))
        return out.path
    }
    fun paths() = doc?.pages?.filter { selected.isEmpty() || it in selected } ?: emptyList()
    fun share(files: List<File>, mime: String) {
        val uris=ArrayList(files.map { FileProvider.getUriForFile(context,"${context.packageName}.files",it) })
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).setType(mime).putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share scans"))
    }
    fun export(pdf: Boolean) {
        val pages=paths(); if(pages.isEmpty())return
        scope.launch { busy=true; runCatching { withContext(Dispatchers.IO) {
            if(!pdf) pages.mapIndexed { i,p -> File(context.cacheDir,"scan-${System.nanoTime()}-$i.jpg").also { out -> val b=Images.decode(p); out.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it) };b.recycle() } }
            else {
                val out=File(context.cacheDir,"scan-${System.nanoTime()}.pdf")
                val document=android.graphics.pdf.PdfDocument()
                try { pages.forEachIndexed { i,p ->
                    val original=Images.decode(p)
                    val limit=when(quality){"Compact"->1200;"Standard"->2000;else->3000}
                    val ratio=minOf(1f,limit.toFloat()/maxOf(original.width,original.height))
                    val b=if(ratio<1f)android.graphics.Bitmap.createScaledBitmap(original,(original.width*ratio).toInt().coerceAtLeast(1),(original.height*ratio).toInt().coerceAtLeast(1),true)else original
                    val autoWidth=original.width
                    val autoHeight=original.height
                    if(b!==original)original.recycle()
                    val w=if(format=="Auto") autoWidth else if(format=="A4")595 else 612
                    val h=if(format=="Auto") autoHeight else if(format=="A4")842 else 792
                    val page=document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(w,h,i+1).create())
                    val scale=minOf(w.toFloat()/b.width,h.toFloat()/b.height)
                    val dw=b.width*scale;val dh=b.height*scale
                    page.canvas.drawColor(android.graphics.Color.WHITE)
                    page.canvas.drawBitmap(b,null,android.graphics.RectF((w-dw)/2,(h-dh)/2,(w+dw)/2,(h+dh)/2),null)
                    document.finishPage(page);b.recycle()
                };out.outputStream().use { document.writeTo(it) } } finally { document.close() };listOf(out)
            }
        } }.onSuccess { analytics.track("export",pages.size); if(saveRequested && pdf){saveFile=it.first();savePicker.launch("scan.pdf");saveRequested=false}else share(it,if(pdf)"application/pdf" else "image/jpeg") }.onFailure { message="Export failed: ${it.localizedMessage}" };busy=false }
    }
    review?.let { pending -> ScanReview(pending.pages.first(),cancel={review=null},retake={review=null;capture()},accept={page,next ->
        val existing=docs.find{it.id==active}
        persist(if(existing==null)listOf(pending.copy(pages=listOf(page)))+docs else docs.map{if(it.id==active)it.copy(pages=it.pages+page)else it})
        active=existing?.id ?: pending.id
        analytics.track("pages_added")
        review=null
        if(next)capture()
    }) }
    crop?.let { original -> CropEditor(Scanner.source(original), done={ result ->
        persist(docs.map { if(it.id==active)it.copy(pages=it.pages.map { p->if(p==original)result else p })else it });crop=null
    },cancel={crop=null}) }
    if(rename) AlertDialog(onDismissRequest={rename=false},title={Text("Document name")},text={TextField(name,{name=it})},confirmButton={TextButton(onClick={ if(name.isNotBlank())persist(docs.map { if(it.id==active)it.copy(name=name.trim())else it });rename=false }){Text("Save")}})
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(doc?.name ?: "Scan",style=MaterialTheme.typography.headlineLarge)
            if(active!=null) TextButton(onClick={active=null;selected=emptySet()}){Text("← Library")}
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotEmpty()) Text(message)
            Row { Button(onClick={capture()},enabled=!busy){Text("Camera")};Spacer(Modifier.width(8.dp));OutlinedButton(onClick={picker.launch(arrayOf("image/*"))},enabled=!busy){Text("Import")}}
            if(doc==null) {
                OutlinedTextField(query,{query=it},label={Text("Search documents")},modifier=Modifier.fillMaxWidth())
                docs.filter { it.name.contains(query,true) }.forEach { d ->
                    Card(Modifier.fillMaxWidth().clickable { active=d.id;selected=emptySet() }) { Row(Modifier.padding(16.dp)) { Checkbox(d.id in selectedDocs,{selectedDocs=if(it)selectedDocs+d.id else selectedDocs-d.id});Column { Text(d.name);Text("${d.pages.size} pages") } } }
                }
                if(selectedDocs.isNotEmpty()) {
                    Button(onClick={val chosen=docs.filter { it.id in selectedDocs };val first=chosen.first();persist(listOf(first.copy(pages=chosen.flatMap { it.pages }))+docs.filter { it.id !in selectedDocs });selectedDocs=emptySet()}){Text("Merge selected")}
                    TextButton(onClick={persist(docs.filter { it.id !in selectedDocs });selectedDocs=emptySet()}){Text("Delete selected documents")}
                }
                if(docs.isEmpty()) Text("Your library is ready. Capture a page or import images to begin.")
            } else {
                Row { TextButton(onClick={name=doc.name;rename=true}){Text("Rename")};TextButton(onClick={persist(listOf(doc.copy(id=java.util.UUID.randomUUID().toString(),name=doc.name+" copy",pages=doc.pages.map {clonePage(it)}))+docs)}){Text("Duplicate")};TextButton(onClick={persist(docs.filter { it.id!=doc.id });active=null}){Text("Delete")}}
                doc.pages.forEachIndexed { i,p ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                        Row { Checkbox(p in selected,{ selected=if(it)selected+p else selected-p });Text("Page ${i+1}") }
                        var bitmap by remember(p) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(p) { bitmap=withContext(Dispatchers.IO){Images.decode(p)} }
                        bitmap?.let { Image(it.asImageBitmap(),"Page ${i+1}",Modifier.fillMaxWidth().height(220.dp)) }
                        Row { TextButton(onClick={if(i>0){val list=doc.pages.toMutableList();java.util.Collections.swap(list,i,i-1);persist(docs.map { if(it.id==active)it.copy(pages=list)else it })}}){Text("Move up")};TextButton(onClick={persist(docs.map { if(it.id==active)it.copy(pages=it.pages.toMutableList().apply { add(i,clonePage(p)) })else it })}){Text("Duplicate")}}
                        TextButton(onClick={crop=p}){Text("Crop / deskew")}
                        Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("Original","Magic","Grayscale","B&W","Rotate").forEach { filter -> TextButton(onClick={scope.launch {busy=true;runCatching { withContext(Dispatchers.IO){if(filter=="Original")Scanner.source(p) else if(filter=="Rotate")Images.render(p,"Original",90) else {val b=Images.decode(p);try {Scanner.process(p,listOf(org.opencv.core.Point(0.0,0.0),org.opencv.core.Point(b.width-1.0,0.0),org.opencv.core.Point(b.width-1.0,b.height-1.0),org.opencv.core.Point(0.0,b.height-1.0)),filter)}finally{b.recycle()}}} }.onSuccess { rendered -> persist(docs.map { if(it.id==active)it.copy(pages=it.pages.mapIndexed { j,old -> if(j==i)rendered else old })else it }) }.onFailure {message="Edit failed"};busy=false }}){Text(filter,style=MaterialTheme.typography.labelSmall)} } }
                    } }
                }
                if(selected.isNotEmpty()) {
                    TextButton(onClick={val chosen=paths();val newDoc=DocumentStore.Document(java.util.UUID.randomUUID().toString(),"New document",chosen);persist(listOf(newDoc)+docs.map {if(it.id==active)it.copy(pages=it.pages.filter {p->p !in selected})else it});selected=emptySet()}){Text("Move to new document")}
                    TextButton(onClick={val newDoc=DocumentStore.Document(java.util.UUID.randomUUID().toString(),"Copied pages",paths().map{clonePage(it)});persist(listOf(newDoc)+docs);selected=emptySet()}){Text("Copy to new document")}

                    TextButton(onClick={persist(docs.map { if(it.id==active)it.copy(pages=it.pages.filter { p->p !in selected })else it });selected=emptySet()}){Text("Delete selected pages")}
                    docs.filter {it.id!=active}.forEach { target ->
                        Row { listOf(false,true).forEach { move -> TextButton(onClick={val chosen=paths();persist(docs.map { when(it.id){target.id->it.copy(pages=it.pages+if(move)chosen else chosen.map {p->clonePage(p)});active->if(move)it.copy(pages=it.pages.filter {p->p !in selected})else it;else->it} });selected=emptySet()}){Text("${if(move)"Move" else "Copy"} to ${target.name}")} } }
                    }
                }
                Row { listOf("Auto","A4","Letter").forEach { option -> FilterChip(format==option,{format=option},label={Text(option)}) } }
                Text("PDF quality")
                Row { listOf("Compact","Standard","High").forEach { option -> FilterChip(quality==option,{quality=option},label={Text(option)}) } }
                Text(when(quality){"Compact"->"Smaller files for messaging · 1200 px";"Standard"->"Balanced detail and file size · 2000 px";else->"Maximum available detail · up to 3000 px"},style=MaterialTheme.typography.bodySmall)
                Row { Button(onClick={export(true)},enabled=!busy){Text("Share PDF")};TextButton(onClick={export(false)},enabled=!busy){Text("Share JPG")};TextButton(onClick={saveRequested=true;export(true)},enabled=!busy){Text("Save PDF")}}
            }
            Text("Documents stay on your device.",style=MaterialTheme.typography.bodySmall)
            Row { Checkbox(analyticsEnabled,{analyticsEnabled=it;analytics.enabled=it});Text("Anonymous usage analytics") }
            UpdateControl()
        }
    }
}

@Composable
private fun UpdateControl() {
    val context=androidx.compose.ui.platform.LocalContext.current
    val updater=remember {AppUpdater(context)}
    val scope=rememberCoroutineScope()
    var update by remember {mutableStateOf<AppUpdater.Update?>(null)}
    var status by remember {mutableStateOf("")}
    LaunchedEffect(Unit){update=withContext(Dispatchers.IO){runCatching{updater.check()}.getOrNull()};if(update!=null)status="Update available"}
    TextButton(onClick={scope.launch {runCatching { if(!updater.installDownloaded()) {val available=update ?: withContext(Dispatchers.IO){updater.check()};if(available!=null){withContext(Dispatchers.IO){updater.download(available)};status="Downloading. Tap again when complete to install."}else status="No update available"} }.onFailure{status="Update failed; try again"}}}){Text(if(status.isEmpty())"App updates" else status)}
}
