package com.zhengui.waterreminder.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val TAG = "CrashHandler"
    private const val LOG_DIR = "crash_logs"
    private const val LOG_FILE = "crash_log.txt"

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashInfo = buildCrashInfo(thread, throwable)
            saveCrashLog(crashInfo)
            Log.e(TAG, "应用崩溃，日志已保存")
        } catch (e: Exception) {
            Log.e(TAG, "保存崩溃日志失败", e)
        }

        // 调用默认处理器
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun buildCrashInfo(thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        sb.appendLine("========== 崩溃日志 ==========")
        sb.appendLine("时间: ${dateFormat.format(Date())}")
        sb.appendLine("线程: ${thread.name}")
        sb.appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine()

        sb.appendLine("异常类型: ${throwable.javaClass.name}")
        sb.appendLine("异常消息: ${throwable.message}")
        sb.appendLine()

        sb.appendLine("堆栈信息:")
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        sb.appendLine(sw.toString())

        // 添加 Cause 链
        var cause = throwable.cause
        while (cause != null) {
            sb.appendLine("Caused by: ${cause.javaClass.name}: ${cause.message}")
            cause.printStackTrace(PrintWriter(sw))
            sb.appendLine(sw.toString())
            cause = cause.cause
        }

        return sb.toString()
    }

    private fun saveCrashLog(crashInfo: String) {
        context?.let { ctx ->
            try {
                val logDir = File(ctx.filesDir, LOG_DIR)
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }

                val logFile = File(logDir, LOG_FILE)
                logFile.appendText(crashInfo)
                logFile.appendText("\n\n")

                Log.d(TAG, "崩溃日志已保存到: ${logFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "保存日志文件失败", e)
            }
        }
    }

    fun getCrashLog(context: Context): String? {
        val logFile = File(context.filesDir, "$LOG_DIR/$LOG_FILE")
        return if (logFile.exists()) {
            logFile.readText()
        } else {
            null
        }
    }

    fun clearCrashLog(context: Context) {
        val logFile = File(context.filesDir, "$LOG_DIR/$LOG_FILE")
        if (logFile.exists()) {
            logFile.delete()
        }
    }
}
