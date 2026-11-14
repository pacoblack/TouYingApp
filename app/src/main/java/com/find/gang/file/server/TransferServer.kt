package com.find.gang.file.server

import android.content.Context
import com.find.gang.file.repository.TransferRepository
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.File

interface TransferStrategy {
    fun canHandle(params: Map<String, List<String>>, files: Map<String, String>): Boolean
    fun handle(params: Map<String, List<String>>, files: Map<String, String>): String
}

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
        val relativePath = params["relative_path"]?.firstOrNull()?.trim() ?: ""

        val combinedSubFolder = listOf(newSubFolder, relativePath)
            .filter { it.isNotEmpty() }
            .joinToString("/")

        val savedFile = repository.saveUploadedFile(tempFilePath, originalFileName, selectedFolder, combinedSubFolder)
        onFileReceived(savedFile.name, savedFile.absolutePath)
        return "已保存至: ${savedFile.parent}"
    }
}

class TransferServer(
    port: Int,
    private val context: Context,
    private val repository: TransferRepository,
    private val strategies: List<TransferStrategy>
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        if (Method.GET == session.method) {
            return when (uri) {
                "/style.css" -> newFixedLengthResponse(Response.Status.OK, "text/css; charset=utf-8", readAssetText("style.css"))
                "/app.js" -> newFixedLengthResponse(Response.Status.OK, "application/javascript; charset=utf-8", readAssetText("app.js"))
                else -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", buildWebPageHtml())
            }
        }

        if (Method.POST == session.method) {
            val files = HashMap<String, String>()
            val json = JSONObject()
            try {
                session.parseBody(files)
                val params = session.parameters
                val matchedStrategy = strategies.firstOrNull { it.canHandle(params, files) }
                if (matchedStrategy != null) {
                    val msg = matchedStrategy.handle(params, files)
                    json.put("success", true)
                    json.put("message", msg)
                    return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json.toString())
                } else {
                    json.put("success", false)
                    json.put("message", "提交内容为空或格式不支持")
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json; charset=utf-8", json.toString())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                json.put("success", false)
                json.put("message", "传输失败: ${e.message}")
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json; charset=utf-8", json.toString())
            } finally {
                files.values.forEach { tempPath ->
                    runCatching { File(tempPath).delete() }
                }
            }
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }

    private fun readAssetText(fileName: String): String {
        return context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun buildWebPageHtml(): String {
        val rootPath = repository.getBaseRootDir().absolutePath
        val folders = repository.getAvailableFolders()
        val defaultSelected = if (folders.contains("Download")) "Download" else ""

        val optionsHtml = folders.joinToString("\n") { folder ->
            val label = if (folder.isEmpty()) "/ (当前存储根目录)" else "/$folder"
            """<option value="$folder" ${if (folder == defaultSelected) "selected" else ""}>$label</option>"""
        }

        return readAssetText("index.html")
            .replace("{{ROOT_PATH}}", rootPath)
            .replace("{{FOLDER_OPTIONS}}", optionsHtml)
    }
}
