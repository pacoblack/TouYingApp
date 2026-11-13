package com.find.gang.file.server

import android.content.Context
import com.find.gang.file.repository.TransferRepository
import fi.iki.elonen.NanoHTTPD

// 策略接口：定义不同表单数据的处理契约
interface TransferStrategy {
    fun canHandle(params: Map<String, List<String>>, files: Map<String, String>): Boolean
    fun handle(params: Map<String, List<String>>, files: Map<String, String>): String
}

// 策略 1：文字推送策略
class TextTransferStrategy(
    private val onTextReceived: (String) -> Unit
) : TransferStrategy {
    override fun canHandle(params: Map<String, List<String>>, files: Map<String, String>): Boolean {
        return !params["text"]?.firstOrNull().isNullOrBlank()
    }

    override fun handle(params: Map<String, List<String>>, files: Map<String, String>): String {
        val textContent = params["text"]!!.first()
        onTextReceived(textContent)
        return "文字已成功发送到投影仪！"
    }
}

// 策略 2：文件上传策略
class FileTransferStrategy(
    private val repository: TransferRepository,
    private val onFileReceived: (String, String) -> Unit
) : TransferStrategy {
    override fun canHandle(params: Map<String, List<String>>, files: Map<String, String>): Boolean {
        return files["file"] != null && !params["file"]?.firstOrNull().isNullOrBlank()
    }

    override fun handle(params: Map<String, List<String>>, files: Map<String, String>): String {
        val tempFilePath = files["file"]!!
        val originalFileName = params["file"]!!.first()
        val selectedFolder = params["folder"]?.firstOrNull() ?: ""
        val newSubFolder = params["new_folder"]?.firstOrNull()?.trim() ?: ""

        val savedFile = repository.saveUploadedFile(tempFilePath, originalFileName, selectedFolder, newSubFolder)
        onFileReceived(originalFileName, savedFile.absolutePath)
        return "文件 [$originalFileName] 已保存至：<br><small>${savedFile.parent}</small>"
    }
}

// HTTP 服务器：按策略链分发请求，并从 assets 加载 HTML 模板
class TransferServer(
    port: Int,
    private val context: Context,
    private val repository: TransferRepository,
    private val strategies: List<TransferStrategy>
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (Method.POST == session.method) {
            val files = HashMap<String, String>()
            try {
                session.parseBody(files)
                val params = session.parameters

                val matchedStrategy = strategies.firstOrNull { it.canHandle(params, files) }
                if (matchedStrategy != null) {
                    val resultMessage = matchedStrategy.handle(params, files)
                    return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", buildSuccessHtml(resultMessage))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "传输失败: ${e.message}")
            }
        }
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", buildWebPageHtml())
    }

    private fun readAssetHtml(fileName: String): String {
        return context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun buildWebPageHtml(): String {
        val rootPath = repository.getBaseRootDir().absolutePath
        val folders = repository.getAvailableFolders()
        val optionsHtml = folders.joinToString("\n") { folder ->
            val label = if (folder.isEmpty()) "/ (存储根目录)" else "/$folder"
            """<option value="$folder" ${if (folder == "Download") "selected" else ""}>$label</option>"""
        }

        return readAssetHtml("index.html")
            .replace("{{ROOT_PATH}}", rootPath)
            .replace("{{FOLDER_OPTIONS}}", optionsHtml)
    }

    private fun buildSuccessHtml(message: String): String {
        return readAssetHtml("success.html")
            .replace("{{MESSAGE}}", message)
    }
}
