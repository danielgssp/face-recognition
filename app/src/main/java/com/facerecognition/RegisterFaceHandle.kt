package com.facerecognition

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.facerecognition.view.activity.RegisterFaceActivity

class RegisterFaceHandle {
    companion object {
        fun registerFaceSDK(activity: FragmentActivity, codOperator: String) {
            val intent = Intent(activity, RegisterFaceActivity::class.java)

            intent.putExtra("COD_OPERATOR", codOperator)
            activity.startActivity(intent)

        }
    }
}