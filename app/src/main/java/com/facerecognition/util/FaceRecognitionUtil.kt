package com.facerecognition.facerecognition

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.luxand.FSDK
import java.io.File
import kotlin.jvm.internal.FunctionReference

class FaceRecognitionUtil {
    companion object {
        private const val database = "faces.dat"
        private const val PERMISSION_ALL = 1

        const val ACTION_REGISTER = 1
        const val actionFaceDetection = 1
        const val parameters = "ContinuousVideoFeed=true;FacialFeatureJitterSuppression=0;RecognitionPrecision=1;Threshold=0.996;Threshold2=0.9995;ThresholdFeed=0.97;MemoryLimit=2000;HandleArbitraryRotations=false;DetermineFaceRotationAngle=false;InternalResizeWidth=384;FaceDetectionThreshold=3;"
        //"ContinuousVideoFeed=true;FacialFeatureJitterSuppression=0;RecognitionPrecision=1;Threshold=0.996;Threshold2=0.9995;ThresholdFeed=0.97;MemoryLimit=2000;HandleArbitraryRotations=false;DetermineFaceRotationAngle=false;InternalResizeWidth=70;FaceDetectionThreshold=3;"

        private var faceSDKInitialized = false
        var sDensity = 1.0f

        val errpos = IntArray(1)
        private val PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
        )

        fun hasPermissions(context: Context, vararg permissions: String): Boolean = permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        fun requestAllPermissions(activity: Activity) {
            if (!hasPermissions(activity, *PERMISSIONS)) {
                ActivityCompat.requestPermissions(activity, PERMISSIONS, PERMISSION_ALL)
            }
        }

        fun getTemplatePath(application: Application) : String{
            return "${application.applicationInfo.dataDir}${File.separator}$database"
        }

        fun initializeFSDK(){
            if (!faceSDKInitialized) {
                faceSDKInitialized = true
                FSDK.Initialize()
            }
        }

        fun showErrorAndClose(error: String, code: Int, mContext: Context) {
            val builder = AlertDialog.Builder(mContext)
            builder.setMessage("$error: $code")
                .setPositiveButton("Ok"
                ) { _, _ ->
                    android.os.Process.killProcess(
                        android.os.Process.myPid()
                    )
                }.show()
        }
    }
}