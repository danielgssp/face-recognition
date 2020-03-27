package com.facerecognition.view

import android.os.Bundle
import com.facerecognition.R
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.facerecognition.facerecognition.FaceRecognitionUtil

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        FaceRecognitionUtil.requestAllPermissions(this)
        val intent = Intent(this, RegisterFace::class.java)
        startActivity(intent)
    }
}