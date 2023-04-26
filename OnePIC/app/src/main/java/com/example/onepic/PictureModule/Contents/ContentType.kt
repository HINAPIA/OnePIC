package com.example.onepic.PictureModule.Contents

enum class ContentType {
    Image,
    Audio,
    Text
}

enum class ActivityType {
    Camera,
    Viewer
}

enum class ContentAttribute(val code: Int ) {
    none(0),
    basic(1),
    burst(2),
    focus(3),
    magic(4),
    modified(5),
    edited(6);

    companion object {
        fun fromCode(code: Int) = values().firstOrNull { it.code == code } ?: none
    }
}
