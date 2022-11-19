package com.jdz.translate

private val mDebug = true

fun logD(message: String) {
    if (mDebug) {
        println(message)
    }
}

fun logE(message: String) {
    println("error:$message")
}