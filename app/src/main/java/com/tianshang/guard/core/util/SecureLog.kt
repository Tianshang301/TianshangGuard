package com.tianshang.guard.core.util

import android.util.Log
import com.tianshang.guard.BuildConfig

object SecureLog {

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    fun w(tag: String, msg: String, e: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (e != null) Log.w(tag, msg, e) else Log.w(tag, msg)
        }
    }

    fun e(tag: String, msg: String, e: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (e != null) Log.e(tag, msg, e) else Log.e(tag, msg)
        }
    }

    fun v(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.v(tag, msg)
    }

    fun v(tag: String, msg: String, e: Throwable) {
        if (BuildConfig.DEBUG) Log.v(tag, msg, e)
    }
}
