package com.find.gang.file

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.find.gang.file.databinding.ActivityMainBinding
import com.find.gang.file.repository.TransferRepository
import com.find.gang.file.viewmodel.TransferViewModel
import java.io.File
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private val viewModel: TransferViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: TransferRepository

    // 记录当前生效的存储目录路径，用于“打开目录”按钮跳转
    private var currentDirPath: String = ""

    // 兼容 Android 10 及以下的权限请求回调
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "未授予存储权限，文件接收功能可能受限", Toast.LENGTH_LONG).show()
        }
    }

    // 目录选择器回调
    private val selectDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val realPath = resolveTreeUriToPath(it)
            if (realPath != null) {
                repository.setCustomRootDir(realPath)
                updateCurrentDirDisplay(realPath)
                Toast.makeText(this, "存储目录已更新为: $realPath", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "暂不支持外置SD卡，请选择内部存储目录", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TransferRepository(this)
        checkStoragePermission()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.vm = viewModel

        setupViews()
        setupListeners()
        observeEvents()
    }

    private fun setupViews() {
        binding.tvFileLog.movementMethod = ScrollingMovementMethod()

        val defaultDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        ).absolutePath
        updateCurrentDirDisplay(defaultDir)
    }

    private fun setupListeners() {
        binding.btnOpenDir.setOnClickListener {
            openDirectory(currentDirPath)
        }

        binding.btnSelectDir.setOnClickListener {
            selectDirLauncher.launch(null)
        }
    }

    private fun observeEvents() {
        viewModel.toastEvent.observe(this) { msg ->
            msg?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeToast()
            }
        }

        viewModel.focusSelectEvent.observe(this) { shouldSelect ->
            if (shouldSelect == true) {
                binding.etReceivedText.requestFocus()
                binding.etReceivedText.selectAll()
            }
        }
    }

    private fun updateCurrentDirDisplay(path: String) {
        currentDirPath = path
        binding.tvCurrentDir.text = buildString {
            append("当前保存目录：")
            append(path)
        }
    }

    /**
     * 兼容投影仪/TV及各版本 Android 系统打开指定文件夹
     */
    private fun openDirectory(dirPath: String) {
        val dirFile = File(dirPath)
        if (!dirFile.exists()) {
            dirFile.mkdirs()
        }

        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val relativePath = if (dirPath.startsWith(rootPath)) {
            dirPath.removePrefix(rootPath).trimStart('/')
        } else {
            ""
        }

        // 方案 1：通过 DocumentsUI 标准 Uri 精准定位到目标文件夹
        try {
            val docId = if (relativePath.isEmpty()) "primary:" else "primary:$relativePath"
            val docUri = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                docId
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
            return
        } catch (_: Exception) {
        }

        // 方案 2：部分第三方 TV 文件管理器支持直接传入路径或 resource/folder MIME
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(dirPath.toUri(), "resource/folder")
            }
            startActivity(intent)
            return
        } catch (_: Exception) {
        }

        // 方案 3：降级打开系统文件管理器（至少让用户能快速进入文件管理浏览）
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivity(intent)
            Toast.makeText(this, "已打开文件管理器，请进入：$relativePath", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "当前系统未找到可用的文件管理器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveTreeUriToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            if (split.isNotEmpty() && "primary".equals(split[0], ignoreCase = true)) {
                val subPath = if (split.size > 1) split[1] else ""
                File(Environment.getExternalStorageDirectory(), subPath).absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        "package:$packageName".toUri()
                    )
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}
