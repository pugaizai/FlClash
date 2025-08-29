package com.follow.clash

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import com.follow.clash.common.GlobalState
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

suspend fun Drawable.getBase64(): String {
    val drawable = this
    return withContext(Dispatchers.IO) {
        val bitmap = drawable.toBitmap()
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP)
    }
}

suspend fun PackageManager.getPackageIconPath(packageName: String): String {
    return withContext(Dispatchers.IO) {
        val icon = getApplicationIcon(packageName)
        val cacheDir = GlobalState.application.cacheDir
        val iconDir = File(cacheDir, "icons").apply {
            if (!exists()) mkdirs()
        }
        val iconFile = File(iconDir, "${packageName}_icon.png")
        return@withContext try {
            saveDrawableToFile(icon, iconFile)
            iconFile.absolutePath
        } catch (_: Exception) {
            val defaultIconFile = File(iconDir, "default_icon.png")
            if (!defaultIconFile.exists()) {
                saveDrawableToFile(defaultActivityIcon, defaultIconFile)
            }
            defaultIconFile.absolutePath
        }
    }
}

private fun saveDrawableToFile(drawable: Drawable, file: File) {
    val bitmap = drawable.toBitmap()
    try {
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
        }
    } finally {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

suspend fun <T> MethodChannel.awaitResult(
    method: String, arguments: Any? = null
): T? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        invokeMethod(method, arguments, object : MethodChannel.Result {
            override fun success(result: Any?) {
                @Suppress("UNCHECKED_CAST") continuation.resume(result as T?)
            }

            override fun error(code: String, message: String?, details: Any?) {
                continuation.resume(null)
            }

            override fun notImplemented() {
                continuation.resume(null)
            }
        })
    }
}

inline fun <reified T : FlutterPlugin> FlutterEngine.plugin(): T? {
    return plugins.get(T::class.java) as T?
}


fun <T> MethodChannel.invokeMethodOnMainThread(
    method: String,
    arguments: Any? = null,
    callback: ((Result<T>) -> Unit)? = null
) {
    Handler(Looper.getMainLooper()).post {
        invokeMethod(method, arguments, object : MethodChannel.Result {
            override fun success(result: Any?) {
                @Suppress("UNCHECKED_CAST")
                callback?.invoke(Result.success(result as T))
            }

            override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                val exception = Exception("MethodChannel error: $errorCode - $errorMessage")
                callback?.invoke(Result.failure(exception))
            }

            override fun notImplemented() {
                val exception = NotImplementedError("Method not implemented: $method")
                callback?.invoke(Result.failure(exception))
            }
        })
    }
}