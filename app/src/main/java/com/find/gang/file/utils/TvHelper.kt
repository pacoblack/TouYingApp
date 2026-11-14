package com.find.gang.file.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import androidx.core.graphics.createBitmap

/**
 * 外观模式：统一封装底层硬件、网络、剪贴板与二维码服务
 */
object TvSystemFacade {

    fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    fun generateQrCode(content: String, size: Int = 400): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                for (x in 0 until size) {
                    for (y in 0 until size) {
                        setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun copyToClipboard(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ProjectorTransferText", text))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resolveTreeUriToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            if (split.isNotEmpty() && "primary".equals(split[0], ignoreCase = true)) {
                val subPath = if (split.size > 1) split[1] else ""
                File(Environment.getExternalStorageDirectory(), subPath).absolutePath
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun openSingleFile(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "无法直接打开该文件：${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * 弹窗 UI 职责分离：专门处理遥控器友好的目录选择与内置文件浏览
 */
object TvDirectoryDialogs {

    fun showSelectDirectoryDialog(
        context: Context,
        onDirectorySelected: (String) -> Unit,
        onRequestSystemPicker: () -> Unit
    ) {
        val options = arrayOf(
            "默认下载目录 (Download)",
            "投影仪快传专用目录 (Download/ProjectorTransfer)",
            "系统电影目录 (Movies)",
            "系统文档目录 (Documents)",
            "调用系统文件管理器自定义选择..."
        )
        val paths = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath,
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ProjectorTransfer").absolutePath,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath,
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
        )

        AlertDialog.Builder(context)
            .setTitle("选择文件保存目录（遥控器上下选择）")
            .setItems(options) { _, which ->
                if (which < paths.size) {
                    val target = paths[which]
                    File(target).apply { if (!exists()) mkdirs() }
                    onDirectorySelected(target)
                } else {
                    onRequestSystemPicker()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    fun showBuiltInFileBrowser(context: Context, directory: File) {
        val files = directory.listFiles()?.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()

        val items = mutableListOf<String>()
        val targets = mutableListOf<File?>()

        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        if (directory.absolutePath != rootPath && directory.parentFile != null) {
            items.add("⬆️ 返回上一级目录")
            targets.add(directory.parentFile)
        }

        if (files.isEmpty()) {
            items.add("（当前文件夹为空）")
            targets.add(null)
        } else {
            files.forEach { file ->
                items.add("${if (file.isDirectory) "📁" else "📄"} ${file.name}")
                targets.add(file)
            }
        }

        AlertDialog.Builder(context)
            .setTitle("内置文件浏览：${directory.name}")
            .setItems(items.toTypedArray()) { _, which ->
                val target = targets[which] ?: return@setItems
                if (target.isDirectory) {
                    showBuiltInFileBrowser(context, target)
                } else {
                    TvSystemFacade.openSingleFile(context, target)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
}
