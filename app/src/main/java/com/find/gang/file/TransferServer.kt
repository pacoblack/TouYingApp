package com.find.gang.file

import android.content.Context
import android.os.Environment
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class TransferServer(
    port: Int,
    private val context: Context,
    private val onTextReceived: (String) -> Unit,
    private val onFileReceived: (String, String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        // 1. 处理 POST 请求（接收文字或文件）
        if (Method.POST == session.method) {
            val files = HashMap<String, String>()
            try {
                // 解析表单与上传的临时文件
                session.parseBody(files)
                val params = session.parms

                // 判断是“发送文字”还是“上传文件”
                val textContent = params["text"]
                if (!textContent.isNullOrBlank()) {
                    onTextReceived(textContent)
                    return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", buildSuccessHtml("文字已成功发送到投影仪！"))
                }

                // 处理上传的文件（NanoHTTPD 会将文件暂存在 files 映射的临时路径中）
                val tempFilePath = files["file"]
                val originalFileName = params["file"]
                if (tempFilePath != null && !originalFileName.isNullOrBlank()) {
                    val savedFile = saveUploadedFile(tempFilePath, originalFileName)
                    onFileReceived(originalFileName, savedFile.absolutePath)
                    return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", buildSuccessHtml("文件 [$originalFileName] 上传成功！"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "传输失败: ${e.message}")
            }
        }

        // 2. 处理 GET 请求（返回自适应手机与电脑的上传网页）
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", buildWebPageHtml())
    }

    private fun saveUploadedFile(tempPath: String, fileName: String): File {
        // 优先保存到公共 Download 目录（Android 10 以下及部分 TV 系统可直接访问），失败则回退到应用专属目录
        val targetDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
        if (!targetDir.exists()) targetDir.mkdirs()

        val destFile = File(targetDir, fileName)
        FileInputStream(File(tempPath)).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    private fun buildWebPageHtml(): String {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>投影仪快传</title>
                <style>
                    body { font-family: -apple-system, sans-serif; max-width: 500px; margin: 30px auto; padding: 0 20px; background: #f5f7fa; color: #333; }
                    .card { background: #fff; padding: 20px; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.08); margin-bottom: 20px; }
                    h2 { margin-top: 0; font-size: 18px; color: #1a73e8; }
                    textarea { width: 100%; height: 100px; padding: 10px; border: 1px solid #ddd; border-radius: 8px; box-sizing: border-box; font-size: 15px; }
                    input[type="file"] { width: 100%; margin: 10px 0 15px; }
                    button { width: 100%; padding: 12px; background: #1a73e8; color: #fff; border: none; border-radius: 8px; font-size: 16px; cursor: pointer; }
                    button:active { background: #1557b0; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h2>1. 推送文字 / 网址到投影仪</h2>
                    <form method="post" action="/">
                        <textarea name="text" placeholder="在此粘贴链接、账号密码或文字内容..." required></textarea>
                        <button type="submit" style="margin-top: 10px;">发送文字</button>
                    </form>
                </div>
                <div class="card">
                    <h2>2. 上传文件 / 安装包到投影仪</h2>
                    <form method="post" action="/" enctype="multipart/form-data">
                        <input type="file" name="file" required />
                        <button type="submit">上传文件</button>
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
            <body style="font-family: sans-serif; text-align: center; padding-top: 60px;">
                <h2 style="color: #2e7d32;">✅ $message</h2>
                <a href="/" style="display: inline-block; margin-top: 20px; padding: 10px 24px; background: #1a73e8; color: #fff; text-decoration: none; border-radius: 6px;">继续发送</a>
            </body>
            </html>
        """.trimIndent()
    }
}
