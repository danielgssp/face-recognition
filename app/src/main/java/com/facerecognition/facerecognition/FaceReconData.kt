package com.facerecognition.facerecognition

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.facerecognition.facerecognition.FaceRecognitionUtil.Companion.ACTION_REGISTER
import com.facerecognition.view.MainActivity

class FaceReconData {
    companion object {
        fun registerFace(ctx: Context, data: String) {
            val bundle = Bundle()
            bundle.putString("data", data)
            bundle.putInt("actionId", ACTION_REGISTER)

            val intent = Intent(ctx, MainActivity::class.java)
            ctx.startActivity(intent)
        }
    }
}