package com.find.gang.file.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.RandomAccessFile
import androidx.core.net.toUri

object ApkInstallHelper {

    /**
     * 检查是否为 APK，若是且后缀不规范则重命名为 .apk，返回最终可用的 File（非 APK 返回 null）
     */
    fun resolveAndPrepareApk(rawFile: File): File? {
        if (!rawFile.exists() || !rawFile.isFile) return null
        if (!rawFile.name.contains("apk", ignoreCase = true)) return null

        val isZipHeader = runCatching {
            RandomAccessFile(rawFile, "r").use { raf ->
                raf.length() > 4 && raf.readShort().toInt() == 0x504B
            }
        }.getOrDefault(false)

        if (!isZipHeader) return null

        if (!rawFile.name.endsWith(".apk", ignoreCase = true)) {
            val targetFile = File(rawFile.parent, "${rawFile.name}.apk")
            if (targetFile.exists()) targetFile.delete() // 防止同名文件导致 renameTo 失败
            return if (rawFile.renameTo(targetFile)) targetFile else rawFile
        }
        return rawFile
    }

    fun installApk(context: Context, apkFile: File, onNeedPermission: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                onNeedPermission()
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    "package:${context.packageName}".toUri()
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
            } else {
                Uri.fromFile(apkFile)
            }
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        context.startActivity(intent)
    }
}

