package com.facerecognition.view.activity

import android.os.Bundle
import android.util.Log
import com.facerecognition.R
import androidx.appcompat.app.AppCompatActivity
import com.facerecognition.DetectionHandle
import com.facerecognition.FaceRecognitionHandle
import com.facerecognition.util.FaceRecognitionUtil

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        FaceRecognitionUtil.requestAllPermissions(this)

//        val detect = DetectionHandle(this, 0.7F)
//        detect.detectPerson = {
//            Log.d("test", "has person")
//        }

//        val recognitionFace = FaceRecognitionHandle(this)
//        recognitionFace.faceAlreadyRegistered = {codeRegistered ->
//            Log.d("test", codeRegistered)
//        }
    }
}