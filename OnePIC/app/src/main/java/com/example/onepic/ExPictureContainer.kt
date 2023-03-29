package com.example.onepic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import java.io.ByteArrayOutputStream
import java.util.jar.Attributes

class ExPictureContainer(private var context: Context) {
    private var mainPicture: Picture
    private var pictureList: ArrayList<Picture> = arrayListOf()

    val testDrawable = listOf<String>("auto_rewind1.jpg", "auto_rewind2.jpg", "auto_rewind3.jpg",
        "auto_rewind4.jpg", "auto_rewind5.jpg", "auto_rewind_best.jpg")

    init {
        for(element in testDrawable){
            pictureList.add(Picture(drawableToByteArray(element)))
        }
        mainPicture = Picture(drawableToByteArray(testDrawable[0]))
    }

    fun getMainPicture(): Picture {
        return mainPicture
    }
    fun setMainPicture(groupID: Int, modifyPicture: Picture) {
        mainPicture = modifyPicture
    }

    fun getPictureList(groupID: Int): ArrayList<Picture> {
        return pictureList
    }
    fun getPictureList(groupID: Int, attribute: String): ArrayList<Picture> {
        return pictureList
    }

    fun drawableToByteArray(drawbleName: String): ByteArray {

        val drawableResId = context.resources.getIdentifier("auto_rewind1.jpg", "drawable", context.packageName)

        val drawable = context.getDrawable(drawableResId)

        // Drawable을 ByteArray로 변환하는 함수
        val bitmap = (drawable as BitmapDrawable).bitmap
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)

        return stream.toByteArray()
    }
}

class Picture(var byteArray: ByteArray) {
}