package com.find.gang.file.repository

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class TransferRepository(private val context: Context) {

    fun getBaseRootDir(): File {
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

        val presets = listOf("Download", "Movies", "Documents", "Pictures")
        presets.forEach { name ->
            val dir = File(root, name)
            if (!dir.exists()) dir.mkdirs()
            if (dir.exists() && dir.isDirectory) {
                folderList.add(name)
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
            val safeSubName = newSubFolder.replace("..", "").replace("/", "_")
            targetDir = File(targetDir, safeSubName)
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val destFile = File(targetDir, fileName)
        FileInputStream(File(tempPath)).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }
}
