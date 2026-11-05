package com.fblah.scan

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DocumentStore(private val context: Context) {
    data class Document(val id: String, val name: String, val pages: List<String>)
    private val root = File(context.filesDir, "documents").apply { mkdirs() }
    private val index = File(root, "index.json")
    fun load(): List<Document> = if (!index.exists()) emptyList() else JSONArray(index.readText()).let { array ->
        (0 until array.length()).map { i -> array.getJSONObject(i).let { obj ->
            val pages = obj.getJSONArray("pages")
            Document(obj.getString("id"), obj.getString("name"), (0 until pages.length()).map { pages.getString(it) })
        } }
    }
    fun save(documents: List<Document>) {
        val data = JSONArray()
        documents.forEach { data.put(JSONObject().put("id", it.id).put("name", it.name).put("pages", JSONArray(it.pages))) }
        val atomic = android.util.AtomicFile(index)
        val stream = atomic.startWrite()
        try { stream.write(data.toString().toByteArray()); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    fun import(uris: List<Uri>): Document {
        val id = UUID.randomUUID().toString()
        val folder = File(root, id).apply { mkdirs() }
        try {
            val pages = uris.mapIndexed { i, uri ->
                val file = File(folder, "$i.original")
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Image could not be opened" }
                    file.outputStream().use { input.copyTo(it) }
                }
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(file.path, options)
                require(options.outWidth > 0) { "Unsupported image" }
                file.path
            }
            return Document(id, "Scan ${java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}", pages)
        } catch (e: Exception) { folder.deleteRecursively(); throw e }
    }
}
