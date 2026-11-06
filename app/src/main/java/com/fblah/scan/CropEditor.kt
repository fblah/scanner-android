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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import org.opencv.core.Point
import kotlin.math.roundToInt

@Composable
fun CropEditor(path: String, done: (String) -> Unit, cancel: () -> Unit) {
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var points by remember(path) { mutableStateOf(emptyList<Point>()) }
    var dragging by remember(path) { mutableStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(path) {
        runCatching { withContext(Dispatchers.IO) {
            val b = Images.decode(path)
            b to Scanner.detect(b)
        } }.onSuccess { bitmap = it.first; points = it.second }
            .onFailure { error = "Could not open scanner: ${it.localizedMessage}" }
    }
    Dialog(onDismissRequest = { if (!busy) cancel() }, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnBackPress = !busy, dismissOnClickOutside = false
    )) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101916), contentColor = Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = cancel, enabled = !busy) { Text("Cancel", color = Color.White) }
                    Text("Crop page", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 10.dp))
                    Button(enabled = points.size == 4 && !busy, onClick = {
                        val corners = points.map { Point(it.x, it.y) }
                        scope.launch {
                            busy = true
                            runCatching { withContext(Dispatchers.IO) { Scanner.process(path, corners) } }
                                .onSuccess(done).onFailure { error = "Crop failed; adjust corners and retry" }
                            busy = false
                        }
                    }) { Text("Apply") }
                }
                Text("Drag near a corner to adjust the page edges.", Modifier.padding(horizontal = 20.dp))
                bitmap?.let { b ->
                    Canvas(Modifier.fillMaxWidth().weight(1f).pointerInput(b, busy) {
                        if (busy) return@pointerInput
                        val inset = 32.dp.toPx()
                        val scale = minOf((size.width - inset * 2) / b.width, (size.height - inset * 2) / b.height)
                        val origin = Offset((size.width - b.width * scale) / 2, (size.height - b.height * scale) / 2)
                        val touchRadius = 40.dp.toPx()
                        detectDragGestures(
                            onDragStart = { pos ->
                                dragging = points.indices.minByOrNull { i ->
                                    val p = points[i]
                                    (Offset(origin.x + p.x.toFloat() * scale, origin.y + p.y.toFloat() * scale) - pos).getDistance()
                                }?.takeIf { i ->
                                    val p = points[i]
                                    (Offset(origin.x + p.x.toFloat() * scale, origin.y + p.y.toFloat() * scale) - pos).getDistance() <= touchRadius
                                } ?: -1
                            },
                            onDragEnd = { dragging = -1 }, onDragCancel = { dragging = -1 },
                            onDrag = { change, delta ->
                                if (dragging >= 0) {
                                    change.consume()
                                    points = points.mapIndexed { i, p -> if (i == dragging) Point(
                                        (p.x + delta.x / scale).coerceIn(0.0, b.width - 1.0),
                                        (p.y + delta.y / scale).coerceIn(0.0, b.height - 1.0)
                                    ) else p }
                                }
                            }
                        )
                    }) {
                        val inset = 32.dp.toPx()
                        val scale = minOf((size.width - inset * 2) / b.width, (size.height - inset * 2) / b.height)
                        val origin = Offset((size.width - b.width * scale) / 2, (size.height - b.height * scale) / 2)
                        drawImage(b.asImageBitmap(), dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                            dstSize = IntSize((b.width * scale).roundToInt(), (b.height * scale).roundToInt()))
                        points.forEachIndexed { i, p ->
                            val a = Offset(origin.x + p.x.toFloat() * scale, origin.y + p.y.toFloat() * scale)
                            val q = points[(i + 1) % points.size]
                            drawLine(Color.Cyan, a, Offset(origin.x + q.x.toFloat() * scale, origin.y + q.y.toFloat() * scale), 2.dp.toPx())
                            drawCircle(Color.Cyan.copy(alpha = 0.18f), 28.dp.toPx(), a)
                            drawCircle(Color.White, 10.dp.toPx(), a)
                            drawCircle(Color(0xFF0D7566), 6.dp.toPx(), a)
                        }
                        // Draw above the handles; the lens never intercepts touch input.
                        if (dragging in points.indices && !busy) {
                            val p = points[dragging]
                            val handle = Offset(origin.x + p.x.toFloat() * scale, origin.y + p.y.toFloat() * scale)
                            val margin = 12.dp.toPx()
                            val side = minOf(144.dp.toPx(), size.width - margin * 2, size.height - margin * 2)
                            if (side > 0f) {
                                val left = if (handle.x < size.width / 2) size.width - side - margin else margin
                                val top = if (handle.y < size.height / 2) size.height - side - margin else margin
                                val center = Offset(left + side / 2, top + side / 2)
                                val zoom = scale * 3f
                                drawRect(Color.Black.copy(alpha = 0.6f), Offset(left - 3.dp.toPx(), top - 3.dp.toPx()),
                                    androidx.compose.ui.geometry.Size(side + 6.dp.toPx(), side + 6.dp.toPx()))
                                clipRect(left, top, left + side, top + side) {
                                    drawRect(Color(0xFF253A32), Offset(left, top), androidx.compose.ui.geometry.Size(side, side))
                                    drawImage(b.asImageBitmap(),
                                        dstOffset = IntOffset((center.x - p.x * zoom).roundToInt(), (center.y - p.y * zoom).roundToInt()),
                                        dstSize = IntSize((b.width * zoom).roundToInt().coerceAtLeast(1), (b.height * zoom).roundToInt().coerceAtLeast(1)))
                                    // Crosshair marks the exact crop point even at an image boundary.
                                    val arm = 12.dp.toPx()
                                    drawLine(Color.Black, center - Offset(arm, 0f), center + Offset(arm, 0f), 3.dp.toPx())
                                    drawLine(Color.Black, center - Offset(0f, arm), center + Offset(0f, arm), 3.dp.toPx())
                                    drawLine(Color.Cyan, center - Offset(arm, 0f), center + Offset(arm, 0f), 1.dp.toPx())
                                    drawLine(Color.Cyan, center - Offset(0f, arm), center + Offset(0f, arm), 1.dp.toPx())
                                }
                                drawRect(Color.White, Offset(left, top), androidx.compose.ui.geometry.Size(side, side), style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                } ?: Spacer(Modifier.weight(1f))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error.isNotEmpty()) Text(error, Modifier.padding(20.dp))
                TextButton(enabled = bitmap != null && !busy, onClick = {
                    scope.launch {
                        busy = true
                        runCatching { withContext(Dispatchers.IO) { Scanner.detect(bitmap!!) } }
                            .onSuccess { points = it }.onFailure { error = "Detection failed; adjust manually" }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Detect edges again", color = Color.White) }
            }
        }
    }
}
