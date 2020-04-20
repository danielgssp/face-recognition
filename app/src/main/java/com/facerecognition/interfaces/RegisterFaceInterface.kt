package com.facerecognition.interfaces

interface RegisterFaceInterface {
    fun onRegister(result: Int, codeRegistered: Long)
    fun onFaceAlreadyRegistered(codeRegistered: Long)
    fun onError(message: String)
}