package com.find.gang.file.server

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

// HTTP 服务器：按策略链分发请求
class TransferServer(
    port: Int,
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

    private fun buildWebPageHtml(): String {
        val rootPath = repository.getBaseRootDir().absolutePath
        val folders = repository.getAvailableFolders()
        val optionsHtml = folders.joinToString("\n") { folder ->
            val label = if (folder.isEmpty()) "/ (存储根目录)" else "/$folder"
            """<option value="$folder" ${if (folder == "Download") "selected" else ""}>$label</option>"""
        }

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>投影仪快传</title>
                <style>
                    body { font-family: -apple-system, sans-serif; max-width: 520px; margin: 24px auto; padding: 0 16px; background: #f5f7fa; color: #333; }
                    .card { background: #fff; padding: 20px; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.08); margin-bottom: 18px; }
                    h2 { margin-top: 0; font-size: 17px; color: #1a73e8; }
                    label { display: block; font-size: 13px; color: #666; margin: 10px 0 5px; }
                    textarea, select, input[type="text"] { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 8px; box-sizing: border-box; font-size: 15px; background: #fff; }
                    textarea { height: 90px; }
                    input[type="file"] { width: 100%; margin: 12px 0; }
                    button { width: 100%; padding: 12px; background: #1a73e8; color: #fff; border: none; border-radius: 8px; font-size: 16px; cursor: pointer; margin-top: 10px; }
                    button:active { background: #1557b0; }
                    .path-tip { font-size: 12px; color: #888; word-break: break-all; margin-top: 4px; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>1. 推送文字 / 网址到投影仪</h2>
                    <form method="post" action="/">
                        <textarea name="text" placeholder="在此粘贴链接、账号密码或文字内容..." required></textarea>
                        <button type="submit">发送文字</button>
                    </form>
                </div>

                <div class="card">
                    <h2>2. 上传文件到指定目录</h2>
                    <div class="path-tip">投影仪基准路径：$rootPath</div>
                    <form method="post" action="/" enctype="multipart/form-data">
                        <label>选择存储目录：</label>
                        <select name="folder">$optionsHtml</select>
                        <label>新建子文件夹（可选，留空则直接存入上方目录）：</label>
                        <input type="text" name="new_folder" placeholder="例如：电影 或 软件安装包" />
                        <label>选择要上传的文件：</label>
                        <input type="file" name="file" required />
                        <button type="submit">开始上传</button>
                    </form>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSuccessHtml(message: String): String {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="font-family: sans-serif; text-align: center; padding-top: 60px; padding-left: 20px; padding-right: 20px;">
                <h2 style="color: #2e7d32;">✅ $message</h2>
                <a href="/" style="display: inline-block; margin-top: 24px; padding: 12px 28px; background: #1a73e8; color: #fff; text-decoration: none; border-radius: 8px;">返回继续传输</a>
            </body>
            </html>
        """.trimIndent()
    }
}
