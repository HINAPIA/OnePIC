package com.example.onepic

import android.content.res.Resources
import android.graphics.*
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRectF
import com.google.mlkit.vision.face.Face
import java.io.ByteArrayOutputStream

class ImageToolModule {
    /**
     * bitmapToByteArray(bitmap: Bitmap): ByteArray
     *      - bitmap을 byteArray로 변환해서 제공
     */
    fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        var outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * byteArrayToBitmap(byteArray: ByteArray): Bitmap
     *      - byteArray를 bitmap으로 변환해서 제공
     */
    fun byteArrayToBitmap(byteArray: ByteArray): Bitmap {
        val bitmap = BitmapFactory.decodeByteArray(byteArray,0,byteArray.size)
        return bitmap
    }


    /**
     * cropBitmap(original: Bitmap, cropRect: Rect): Bitmap
     *      - original 이미지를 cropRect에 맞게 잘라 반환
     */
    fun cropBitmap(original: Bitmap, cropRect: Rect): Bitmap {

        var width = (cropRect.right - cropRect.left)
        var height = cropRect.bottom - cropRect.top
        var startX = cropRect.left
        var startY = cropRect.top
        if (startX < 0)
            startX = 0
        else if (startX+width > original.width)
            width = original.width-startX
        if (startY < 0)
            startY = 0
        else if(startY+height > original.height)
            height = original.height-startY

        val result = Bitmap.createBitmap(
            original
            , startX         // X 시작위치
            , startY         // Y 시작위치
            , width          // 넓이
            , height         // 높이
        )
        // 이미지 할당 해제
//        if (result != original) {
//            original.recycle()
//        }
        return result
    }

    /**
     * circleCropBitmap(bitmap: Bitmap): Bitmap
     *      - bitmap을 둥글게 잘라 반환
     */
    fun circleCropBitmap(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(output)
        val color = -0xbdbdbe
        val paint = Paint()
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        paint.isAntiAlias = true
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = color
        // canvas.drawRoundRect(rectF, roundPx, roundPx, paint);
        canvas.drawCircle(
            (bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat(),
            (bitmap.width / 2).toFloat(), paint
        )
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }

    /**
     * overlayBitmap(original: Bitmap, add: Bitmap, optimizationX:Int, optimizationY:Int): Bitmap
     *      - original 이미지에 add 이미지를 (x, y) 좌표에 추가,
     *        만들어진 bitmap을 반환
     */
    fun overlayBitmap(original: Bitmap, add: Bitmap, x:Int, y:Int): Bitmap {

        var startX = x
        var startY = y

        if (startX < 0) {
            startX = 0
        }
        if (startY < 0) {
            startY = 0
        }

        //결과값 저장을 위한 Bitmap
        val resultOverlayBmp = Bitmap.createBitmap(
            original.width, original.height, original.config
        )

        //캔버스를 통해 비트맵을 겹치기한다.
        val canvas = Canvas(resultOverlayBmp)
        canvas.drawBitmap(original, Matrix(), null)
        canvas.drawBitmap(add, startX.toFloat(), startY.toFloat(), null)

        return resultOverlayBmp
    }

    /**
     * floatToDp(f : Float):
     *        float 값을 dp값으로 변화해서 반환
     */
    fun floatToDp(f : Float):Int {
        return (f * Resources.getSystem().displayMetrics.density + 0.5f).toInt()
    }

    /**
     * drawDetectionResult(bitmap: Bitmap, detectionResults: ArrayList<Face>, customColor: Int) : Bitmap
     *      - detection 결과를 통해 해당 detection을 이미지 위에 그린 후 bitmap 결과를 받환
     *          ( detectionResult가 ArrayList<Face>인 경우는 얼굴 분석 결과를 그림
     *                              List<DetectionResult>인 경우는 객체 분석 결과를 그림 )
     */
    fun drawDetectionResult(
        bitmap: Bitmap,
        detectionResults: ArrayList<Face>,
        customColor: Int
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

        detectionResults.forEach {
            // draw bounding box
            pen.color = Color.parseColor("#B8C5BB")
            //pen.color = customColor
            pen.strokeWidth = floatToDp(3F).toFloat()
            pen.style = Paint.Style.STROKE
            val box = it.boundingBox.toRectF()
            canvas.drawRoundRect(box, floatToDp(10F).toFloat(), floatToDp(10F).toFloat(), pen)

        }
        return outputBitmap
    }


    /**
     * getBitmapClickPoint( clickPoint: PointF, imageView: ImageView): Point
     *      - imageView에 clickEvent가 발생했을 때,
     *        clickPoint를 사진 Bitmap에 맞춰 비율 조정을 해서
     *        비율 보정된 새로운 point를 반환
     */
    fun getBitmapClickPoint(clickPoint: PointF, imageView: ImageView): Point {

        var bitmap: Bitmap = imageView.drawable.toBitmap()
        //bitmap = Bitmap.createScaledBitmap(bitmap, 1080, 810, true)

        // imageView width, height 가져오기
        val viewWidth = imageView.width
        val viewHeight = imageView.height

        // 실제 이미지일 때 포인트 위치
        val newPointX = (bitmap.width * clickPoint.x) / viewWidth
        val newPointY = (bitmap.height * clickPoint.y) / viewHeight

        // 수정된 이미지 포인트
        return Point(newPointX.toInt(), newPointY.toInt())
    }

    /**
     * checkPointInRect(point: Point, rect: Rect):
     *          point 가 rect 안에 존재하는 지를 알아낸다.
     *          존재하면 true 존재하지 않으면 false
     */
    fun checkPointInRect(point: Point, rect: Rect): Boolean {
        // 포인트가 boundingBox 안에 존재할 경우에만 실행
        // -> boundinBox.left <= x <= boundingBox.right && boundingBox.top <= y <= boundingBox.bottom
        return point.x >= rect.left && point.x <= rect.right &&
                point.y >= rect.top && point.y <= rect.bottom
    }

}