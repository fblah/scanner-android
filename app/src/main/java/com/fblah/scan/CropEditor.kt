package com.fblah.scan

import androidx.compose.runtime.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.opencv.core.Point

@Composable
fun CropEditor(path: String, done: (String)->Unit, cancel: ()->Unit) {
    var bitmap by remember(path){mutableStateOf<android.graphics.Bitmap?>(null)}
    var points by remember(path){mutableStateOf(emptyList<Point>())}
    var busy by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(path){runCatching{withContext(Dispatchers.IO){val b=Images.decode(path); b to Scanner.detect(b)}}.onSuccess{bitmap=it.first;points=it.second}.onFailure{error="Could not open scanner: ${it.localizedMessage}"}}
    AlertDialog(onDismissRequest=cancel,title={Text("Adjust page corners")},text={Column{
        Text("Drag each corner to the edge of your page.")
        bitmap?.let { b ->
            var dragging by remember{mutableStateOf(0)}
            Canvas(Modifier.fillMaxWidth().aspectRatio(b.width.toFloat()/b.height).pointerInput(points){detectDragGestures(onDragStart={pos->dragging=points.indices.minBy{val p=points[it];kotlin.math.hypot((p.x/b.width*size.width-pos.x),(p.y/b.height*size.height-pos.y))}},onDrag={change,delta->change.consume();points=points.mapIndexed{i,p->if(i==dragging)Point((p.x+delta.x/size.width*b.width).coerceIn(0.0,b.width.toDouble()-1),(p.y+delta.y/size.height*b.height).coerceIn(0.0,b.height.toDouble()-1))else p}})} ) {
                drawImage(b.asImageBitmap(),dstSize=androidx.compose.ui.unit.IntSize(size.width.toInt(),size.height.toInt()))
                points.forEachIndexed{i,p->val a=Offset((p.x/b.width*size.width).toFloat(),(p.y/b.height*size.height).toFloat());val q=points[(i+1)%4];drawLine(Color.Cyan,a,Offset((q.x/b.width*size.width).toFloat(),(q.y/b.height*size.height).toFloat()),3f);drawCircle(Color.Cyan,12f,a)}
            }
        };if(busy)LinearProgressIndicator();Text(error)
    }},confirmButton={TextButton(enabled=points.size==4&&!busy,onClick={scope.launch{busy=true;runCatching{withContext(Dispatchers.IO){Scanner.process(path,points)}}.onSuccess(done).onFailure{error="Crop failed; adjust corners and retry"};busy=false}}){Text("Apply")}},dismissButton={TextButton(onClick=cancel){Text("Cancel")}})
}
