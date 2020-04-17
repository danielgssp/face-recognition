package com.facerecognition.interfaces

interface DetectionFragmentInterface {
    fun confidenceRate(id: String, classification: String, rate: Float)
}