package com.find.gang.file.utils

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.core.net.toUri
import java.io.File

/**
 * 责任链抽象节点
 */
abstract class DirectoryOpener {
    private var next: DirectoryOpener? = null

    fun setNext(nextOpener: DirectoryOpener): DirectoryOpener {
        this.next = nextOpener
        return nextOpener
    }

    fun handle(context: Context, dirFile: File, relativePath: String) {
        if (!tryOpen(context, dirFile, relativePath)) {
            next?.handle(context, dirFile, relativePath)
        }
    }

    protected abstract fun tryOpen(context: Context, dirFile: File, relativePath: String): Boolean
}

/**
 * 节点 1：系统原生 DocumentsUI 精准定位
 */
class DocumentsUiOpener : DirectoryOpener() {
    override fun tryOpen(context: Context, dirFile: File, relativePath: String): Boolean {
        return try {
            val docId = if (relativePath.isEmpty()) "primary:" else "primary:$relativePath"
            val docUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                docId
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * 节点 2：第三方文件管理器 resource/folder 协议
 */
class ResourceFolderOpener : DirectoryOpener() {
    override fun tryOpen(context: Context, dirFile: File, relativePath: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(dirFile.absolutePath.toUri(), "resource/folder")
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * 节点 3：主流国产投影仪/TV 厂商文件管理器包名直达
 */
class TvVendorPackageOpener : DirectoryOpener() {
    private val vendorPackages = listOf(
        "com.xgimi.filemanager",         // 极米
        "com.dangbei.leard.filemanager", // 当贝
        "com.jmgo.filemanager",          // 坚果
        "com.android.rk",                // 瑞芯微通用
        "com.mitv.filemanager",          // 小米TV
        "com.estrongs.android.pop",      // ES文件浏览器
        "com.xb.filemanager"             // 小白文件管理器
    )

    override fun tryOpen(context: Context, dirFile: File, relativePath: String): Boolean {
        for (pkg in vendorPackages) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
            try {
                context.startActivity(launchIntent)
                Toast.makeText(context, "已打开文件管理器，请进入：$relativePath", Toast.LENGTH_LONG).show()
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }
}

/**
 * 节点 4：通用系统目录树选择器兜底
 */
class DocumentTreeFallbackOpener : DirectoryOpener() {
    override fun tryOpen(context: Context, dirFile: File, relativePath: String): Boolean {
        return try {
            context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
            Toast.makeText(context, "已打开文件管理器，请进入：$relativePath", Toast.LENGTH_LONG).show()
            true
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * 节点 5：终极兜底 —— App 内置遥控器文件浏览器
 */
class BuiltInBrowserOpener(
    private val onShowBuiltInBrowser: (File) -> Unit
) : DirectoryOpener() {
    override fun tryOpen(context: Context, dirFile: File, relativePath: String): Boolean {
        onShowBuiltInBrowser(dirFile)
        return true
    }
}

/**
 * 责任链组装工厂
 */
object DirectoryOpenChainFactory {
    fun open(context: Context, dirPath: String, onShowBuiltInBrowser: (File) -> Unit) {
        val dirFile = File(dirPath).apply { if (!exists()) mkdirs() }
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val relativePath = if (dirPath.startsWith(rootPath)) {
            dirPath.removePrefix(rootPath).trimStart('/')
        } else {
            ""
        }

        val head = DocumentsUiOpener()
        head.setNext(ResourceFolderOpener())
            .setNext(TvVendorPackageOpener())
            .setNext(DocumentTreeFallbackOpener())
            .setNext(BuiltInBrowserOpener(onShowBuiltInBrowser))

        head.handle(context, dirFile, relativePath)
    }
}
