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

class MainActivity : AppCompatActivity() {

    private val viewModel: TransferViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: TransferRepository

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
            // 持久化 URI 授权，防止部分系统在重启后回收权限
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
        // 允许文件接收记录文本框内部垂直滚动
        binding.tvFileLog.movementMethod = ScrollingMovementMethod()

        // 初始化显示当前保存目录（如果 TransferRepository 有对应 getter，可直接替换默认值）
        val defaultDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        updateCurrentDirDisplay(defaultDir)
    }

    private fun setupListeners() {
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
        binding.tvCurrentDir.text = "当前保存目录：$path"
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
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } catch (e: Exception) {
                    // 部分TV/定制系统可能不支持按包名跳转，回退到通用权限设置页
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
