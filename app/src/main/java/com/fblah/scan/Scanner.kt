package com.fblah.scan

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.io.File

object Scanner {
    init { check(OpenCVLoader.initLocal()) { "Scanner engine unavailable" } }
    private fun ordered(points: List<Point>): List<Point> = listOf(points.minBy {it.x+it.y},points.maxBy {it.x-it.y},points.maxBy {it.x+it.y},points.minBy {it.x-it.y})
    fun source(path:String):String = File(path+".source").let { if(it.exists())it.readText()else path }
    fun detect(bitmap: Bitmap): List<Point> {
        val src=Mat();val small=Mat();val gray=Mat();val edges=Mat();val hierarchy=Mat()
        val contours=ArrayList<MatOfPoint>()
        try {
            Utils.bitmapToMat(bitmap,src)
            val scale=720.0/maxOf(bitmap.width,bitmap.height)
            Imgproc.resize(src,small,Size(),scale,scale)
            Imgproc.cvtColor(small,gray,Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray,gray,Size(5.0,5.0),0.0)
            Imgproc.Canny(gray,edges,30.0,110.0)
            Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE)
            var best: List<Point>?=null;var area=small.total()*0.08
            contours.forEach { c ->
                val curve=MatOfPoint2f(*c.toArray());val approx=MatOfPoint2f()
                try { Imgproc.approxPolyDP(curve,approx,0.02*Imgproc.arcLength(curve,true),true)
                    val polygon=MatOfPoint(*approx.toArray())
                    try {val a=Imgproc.contourArea(polygon);if(approx.total()==4L && a>area && Imgproc.isContourConvex(polygon)){best=ordered(approx.toList()).map{Point(it.x/scale,it.y/scale)};area=a}}finally{polygon.release()}
                }finally{curve.release();approx.release()}
            }
            return best ?: listOf(Point(0.0,0.0),Point(bitmap.width.toDouble()-1,0.0),Point(bitmap.width.toDouble()-1,bitmap.height.toDouble()-1),Point(0.0,bitmap.height.toDouble()-1))
        }finally{contours.forEach{it.release()};listOf(src,small,gray,edges,hierarchy).forEach{it.release()}}
    }
    fun process(path: String, points: List<Point>, filter: String="Magic"): String {
        val bitmap=Images.decode(path);val src=Mat();val warp=Mat();val rgb=Mat();val out=Mat()
        val from=MatOfPoint2f(*points.toTypedArray())
        fun distance(a:Point,b:Point)=kotlin.math.hypot(a.x-b.x,a.y-b.y)
        val w=maxOf(distance(points[0],points[1]),distance(points[3],points[2])).toInt().coerceIn(1,3000)
        val h=maxOf(distance(points[0],points[3]),distance(points[1],points[2])).toInt().coerceIn(1,3000)
        val to=MatOfPoint2f(Point(0.0,0.0),Point(w.toDouble()-1,0.0),Point(w.toDouble()-1,h.toDouble()-1),Point(0.0,h.toDouble()-1))
        val transform=Imgproc.getPerspectiveTransform(from,to)
        try {
            Utils.bitmapToMat(bitmap,src);Imgproc.warpPerspective(src,warp,transform,Size(w.toDouble(),h.toDouble()))
            if(filter=="Original")warp.copyTo(out) else {
                Imgproc.cvtColor(warp,rgb,if(filter=="Magic")Imgproc.COLOR_RGBA2RGB else Imgproc.COLOR_RGBA2GRAY)
                val channels=ArrayList<Mat>();Core.split(rgb,channels)
                try { channels.forEach { channel ->
                    val bg=Mat();val kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,Size(7.0,7.0))
                    try {Imgproc.dilate(channel,bg,kernel);Imgproc.medianBlur(bg,bg,21);Core.divide(channel,bg,channel,255.0);channel.convertTo(channel,-1,1.12,-28.0)}finally{bg.release();kernel.release()}
                };Core.merge(channels,out) }finally{channels.forEach{it.release()}}
                if(filter=="B&W")Imgproc.adaptiveThreshold(out,out,255.0,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,51,10.0)
            }
            val result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Utils.matToBitmap(out,result)
            val file=File(File(path).parentFile,"${java.util.UUID.randomUUID()}.jpg")
            File(file.path+".source").writeText(source(path))
            file.outputStream().use {result.compress(Bitmap.CompressFormat.JPEG,90,it)};result.recycle();return file.path
        }finally{bitmap.recycle();listOf(src,warp,rgb,out,from,to,transform).forEach{it.release()}}
    }
}
