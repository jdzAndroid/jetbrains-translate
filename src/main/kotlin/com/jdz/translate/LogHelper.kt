package com.jdz.translate

private const val mDebug = true

fun logD(message: String) {
    if (mDebug) {
        println(message)
    }
}

fun logE(message: String) {
    println("error:$message")
}