package com.facerecognition

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import com.facerecognition.interfaces.DetectionFragmentInterface
import com.facerecognition.view.fragment.DetectionFragment

open class DetectionHandle() {
    private var detectionFragment: DetectionFragment? = null
    private var activity: FragmentActivity? = null
    var detectPerson: (() -> Unit)? = null
    var getDataObjectDetect: ((id: String, name: String, rate: Float) -> Unit)? = null

    constructor(fragmentActivity:FragmentActivity, rateInput: Float) : this() {
         this.activity = fragmentActivity

        buildLayoutFragment()

        val ft: FragmentTransaction? = activity?.supportFragmentManager?.beginTransaction()
        detectionFragment = DetectionFragment(
            object : DetectionFragmentInterface {
                override fun confidenceRate(id: String, classification: String, rate: Float) {
                    getDataObjectDetect?.invoke(id, classification, rate)
                    if (classification == "person" && rate >= rateInput) {
                        detectPerson?.invoke()
                    }
                }
            }
        )

        ft?.replace(R.id.detection_fragment, detectionFragment!!)
        ft?.commit()
    }

    fun closeDetectionFragment() {
        activity?.supportFragmentManager
            ?.beginTransaction()
            ?.remove(detectionFragment!!)
            ?.commit()
    }

    private fun buildLayoutFragment() {
       try {
           val frameLayout = FrameLayout(activity?.applicationContext!!)
           val layoutParams: FrameLayout.LayoutParams = FrameLayout.LayoutParams(1,1)
           frameLayout.id = R.id.detection_fragment
           frameLayout.layoutParams = layoutParams

           activity?.addContentView(frameLayout, layoutParams)
       } catch (e: Exception) {
           e.printStackTrace()
       }
    }
}