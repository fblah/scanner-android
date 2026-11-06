package com.fblah.scan

import androidx.compose.runtime.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*

@Composable
fun ScanReview(path:String, cancel:()->Unit, retake:()->Unit, accept:(String,Boolean)->Unit) {
    var page by remember(path){mutableStateOf(path)}
    var crop by remember{mutableStateOf(false)}
    var bitmap by remember{mutableStateOf<android.graphics.Bitmap?>(null)}
    LaunchedEffect(page){bitmap=withContext(Dispatchers.IO){Images.decode(page)}}
    if(crop) CropEditor(Scanner.source(page),{page=it;crop=false},{crop=false})
    Dialog(onDismissRequest=cancel,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Review scan",style=MaterialTheme.typography.headlineMedium)
                Text("Check the edges and sharpness before keeping this page.")
                bitmap?.let{Image(it.asImageBitmap(),"Captured page",Modifier.weight(1f).fillMaxWidth())} ?: Spacer(Modifier.weight(1f))
                Row {TextButton(onClick=cancel){Text("Discard")};TextButton(onClick=retake){Text("Retake")};TextButton(onClick={crop=true}){Text("Adjust crop")}}
                Button(onClick={accept(page,true)},modifier=Modifier.fillMaxWidth()){Text("Keep & next page")}
                OutlinedButton(onClick={accept(page,false)},modifier=Modifier.fillMaxWidth()){Text("Keep & finish")}
            }
        }
    }
}
