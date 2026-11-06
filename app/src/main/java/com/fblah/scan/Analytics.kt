package com.fblah.scan

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Only enumerated event names and numeric counts enter telemetry. No filenames, IDs or images. */
class Analytics(context: Context) {
    private val prefs=context.getSharedPreferences("analytics",0)
    private val executor=Executors.newSingleThreadExecutor()
    var enabled: Boolean
        get()=prefs.getBoolean("enabled",true)
        set(value){prefs.edit().putBoolean("enabled",value).apply()}
    fun track(name:String,count:Int=1){
        if(!enabled || name !in setOf("pageview","pages_added","export","library_changed","crop_adjusted","filter_changed"))return
        executor.execute {
            if(!enabled)return@execute
            runCatching {
                val payload=JSONObject().put("s","scan").put("t",if(name=="pageview")"pageview" else "event")
                    .put("u","https://scan.android/#/").put("n",name).put("ts",System.currentTimeMillis())
                    .put("p",JSONObject().put("display_mode","android").put("count",count))
                val connection=URL("https://analytics.s2.fblah.com/api/event").openConnection() as HttpURLConnection
                try {connection.requestMethod="POST";connection.connectTimeout=5000;connection.readTimeout=5000;connection.doOutput=true;connection.setRequestProperty("Content-Type","text/plain");connection.outputStream.use{it.write(payload.toString().toByteArray())};connection.inputStream.close()}finally{connection.disconnect()}
            }
        }
    }
}
