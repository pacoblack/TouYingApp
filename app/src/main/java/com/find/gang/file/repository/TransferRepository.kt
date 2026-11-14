package com.find.gang.file.repository

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.core.content.edit

class TransferRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("transfer_config", Context.MODE_PRIVATE)

    fun setCustomRootDir(path: String) {
        prefs.edit { putString("custom_root_dir", path) }
    }

    fun getBaseRootDir(): File {
        val customPath = prefs.getString("custom_root_dir", null)
        if (!customPath.isNullOrEmpty()) {
            val customDir = File(customPath)
            if (!customDir.exists()) customDir.mkdirs()
            if (customDir.exists() && customDir.canWrite()) {
                return customDir
            }
        }

        val publicRoot = Environment.getExternalStorageDirectory()
        return if (publicRoot != null && publicRoot.canWrite()) {
            publicRoot
        } else {
            context.getExternalFilesDir(null) ?: context.filesDir
        }
    }

    fun getAvailableFolders(): List<String> {
        val root = getBaseRootDir()
        val folderList = mutableListOf<String>()
        folderList.add("") // 代表根目录本身

        // 仅在系统默认外部存储根目录下才自动创建四大预设文件夹，避免污染用户自定义目录
        val isDefaultRoot = root.absolutePath == Environment.getExternalStorageDirectory()?.absolutePath
        if (isDefaultRoot) {
            val presets = listOf("Download", "Movies", "Documents", "Pictures")
            presets.forEach { name ->
                val dir = File(root, name)
                if (!dir.exists()) dir.mkdirs()
                if (dir.exists() && dir.isDirectory) {
                    folderList.add(name)
                }
            }
        }

        root.listFiles()?.forEach { file ->
            if (file.isDirectory && !file.name.startsWith(".") && !folderList.contains(file.name)) {
                folderList.add(file.name)
            }
        }
        return folderList
    }

    fun saveUploadedFile(
        tempPath: String,
        fileName: String,
        selectedFolder: String,
        newSubFolder: String
    ): File {
        val root = getBaseRootDir()
        var targetDir = if (selectedFolder.isNotEmpty()) File(root, selectedFolder) else root

        if (newSubFolder.isNotEmpty()) {
            val subSegments = newSubFolder.split("/")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "." && it != ".." }
            for (segment in subSegments) {
                targetDir = File(targetDir, segment)
            }
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val destFile = getUniqueFile(targetDir, fileName)
        FileInputStream(File(tempPath)).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }
        return destFile
    }

    // 重名文件自动递增编号（例如 report.pdf -> report(1).pdf），防止意外覆盖
    private fun getUniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate

        val nameWithoutExt = fileName.substringBeforeLast(".", fileName)
        val ext = if (fileName.contains(".")) fileName.substringAfterLast(".") else ""
        var index = 1
        while (candidate.exists()) {
            val newName = if (ext.isNotEmpty()) "$nameWithoutExt($index).$ext" else "$nameWithoutExt($index)"
            candidate = File(dir, newName)
            index++
        }
        return candidate
    }
}
