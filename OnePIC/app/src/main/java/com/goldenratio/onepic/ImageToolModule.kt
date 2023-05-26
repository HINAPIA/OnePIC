package com.goldenratio.onepic

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toRectF
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class ImageToolModule {
    /**
     * bitmapToByteArray(bitmap: Bitmap): ByteArray
     *      - bitmap을 byteArray로 변환해서 제공
     */
     fun bitmapToByteArray(bitmap: Bitmap, byteArray: ByteArray?): ByteArray {

        var matrix = Matrix()

        if (byteArray != null) {
            matrix = bitmapRotation(byteArray, -1)
        } else {
            matrix.postRotate(0f)
        }

        val newBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val outputStream = ByteArrayOutputStream()
        newBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        Thread.sleep(1000)
        return outputStream.toByteArray()

    }

    /**
     * byteArrayToBitmap(byteArray: ByteArray): Bitmap
     *      - byteArray를 bitmap으로 변환해서 제공
     */
    fun byteArrayToBitmap(byteArray: ByteArray): Bitmap {

        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.RGB_565

        val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)

        if (bitmap == null) {
            // bitmap이 null인 경우 예외 처리를 수행합니다.
            // 예를 들어, 사용자에게 오류 메시지를 표시하거나 기본 이미지를 반환 할 수 있습니다.
//            while(bitmap==null){
//                bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
//                Log.d("bitmap while", "while in")
//            }
            return bitmap

        } else {
            val matrix = bitmapRotation(byteArray , 1)
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

    }

    @Throws(IOException::class)
    fun getBytes(inputStream: InputStream): ByteArray {
        val byteBuffer = ByteArrayOutputStream()
        val bufferSize = 1024
        val buffer = ByteArray(bufferSize)
        var len = 0
        while (inputStream.read(buffer).also { len = it } != -1) {
            byteBuffer.write(buffer, 0, len)
        }
        byteBuffer.close()
        inputStream.close()
        return byteBuffer.toByteArray()
    }

    @SuppressLint("Range")
    fun getUriFromPath(context: Context ,filePath: String): Uri { // filePath String to Uri

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            null, "_data = '$filePath'", null, null)
        var uri: Uri
        if(cursor!=null) {
            cursor!!.moveToNext()
            val id = cursor.getInt(cursor.getColumnIndex("_id"))
            uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                id.toLong()
            )
            cursor.close()
        }
        else {
            return Uri.parse("Invalid path")
        }
        return uri

    }

    fun bitmapRotation(byteArray: ByteArray, value: Int) : Matrix{
        val inputStream: InputStream = ByteArrayInputStream(byteArray)

        val exif = ExifInterface(inputStream)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f * value)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f * value)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f * value)
            else -> matrix.postRotate(0f)
        }
        return matrix
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
        if (startX + width > original.width)
            width = original.width - startX
        if (startY < 0)
            startY = 0
        if (startY + height > original.height)
            height = original.height - startY


        Log.d("imageTool", "~~~~~~!! $startX | $startY | $width | $height")

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
    fun floatToDp(context: Context, px : Float): Float {
//        return (f * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

        val resources = context.resources
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_PX,
            px,
            resources.displayMetrics
        )
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
        var outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        detectionResults.forEach {
            outputBitmap = drawDetectionResult(outputBitmap, it.boundingBox.toRectF(), customColor)
        }
        return outputBitmap
    }

    fun drawDetectionResult(
        bitmap: Bitmap,
        box: RectF,
        customColor: Int
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

            // draw bounding box
//            pen.color = context.resources.getColor(R.color.white)
            pen.color = customColor
            pen.strokeWidth = 30f
            pen.style = Paint.Style.STROKE
            canvas.drawArc(box, -30f, 70f, false, pen)
            canvas.drawArc(box, 140f, 70f, false, pen)
        return outputBitmap
    }

    /**
     * getBitmapClickPoint( clickPoint: PointF, imageView: ImageView): Point
     *      - imageView에 clickEvent가 발생했을 때,
     *        clickPoint를 사진 Bitmap에 맞춰 비율 조정을 해서
     *        비율 보정된 새로운 point를 반환
     */
    fun getBitmapClickPoint(clickPoint: PointF, imageView: ImageView): Point? {

        if(imageView.drawable == null) return null

        val bitmap: Bitmap = imageView.drawable.toBitmap()
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

    fun getFitCenterPaddingValue(imageView: ImageView): Int {

        if(imageView.drawable == null) return 0

        val bitmap: Bitmap = imageView.drawable.toBitmap()

        // imageView width, height 가져오기
        val viewWidth = imageView.width
        val viewHeight = imageView.height

        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        val alpha = viewWidth/bitmapWidth

        return if( viewHeight > bitmapHeight * alpha ){
            viewHeight - bitmapHeight * alpha
        } else {
            viewHeight
        }

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

    /**
     * showView(view: View, isShow: Boolean)
     *          view를 보여주고 숨기는 함수
     *          false면 gone, true면 visible
     */
    fun showView(view: View, isShow: Boolean){
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.Main) {
                if (isShow) view.visibility = View.VISIBLE
                else view.visibility = View.GONE
            }
        }
    }

    fun adjustMinMaxValues(arr: ArrayList<Double>, newMin: Double, newMax: Double): ArrayList<Double> {
        val minValue = arr.minOrNull() ?: return arr
        val maxValue = arr.maxOrNull() ?: return arr

        val adjustedArr = arrayListOf<Double>()
        val minMaxDiff = maxValue - minValue
        val newMinMaxDiff = newMax - newMin

        for (i in arr.indices) {
            val normalizedValue = (arr[i] - minValue) / minMaxDiff.toDouble() // 정규화된 값 계산
            adjustedArr.add(normalizedValue * newMinMaxDiff.toDouble() + newMin) // 비율에 따라 값 변환

            if(adjustedArr[i].isNaN()) {
                adjustedArr[i] = 0.0
                Log.d("anaylsis", "adjustMinMaxValues isNan")
            }

        }

        return adjustedArr
    }

}