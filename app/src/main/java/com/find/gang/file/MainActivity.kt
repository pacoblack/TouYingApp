package com.find.gang.file

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.find.gang.file.databinding.ActivityMainBinding
import com.find.gang.file.viewmodel.TransferViewModel
import java.io.File

class MainActivity : AppCompatActivity() {

    private val viewModel: TransferViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    // 目录选择器回调
    private val selectDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val realPath = resolveTreeUriToPath(it)
            if (realPath != null) {
                // 直接写入 Repository 的 SharedPreferences，TransferServer 会实时生效
                TransferRepository(this).setCustomRootDir(realPath)
                Toast.makeText(this, "存储目录已更新为: $realPath", Toast.LENGTH_SHORT).show()
                // 如果你界面上有显示当前目录的 TextView，可以在这里顺便刷新文本
                // binding.tvCurrentDir.text = realPath
            } else {
                Toast.makeText(this, "暂不支持外置SD卡，请选择内部存储目录", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkStoragePermission()

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.vm = viewModel

        setupListeners()
        observeEvents()
    }

    private fun setupListeners() {
        // 假设你的布局中有一个选择目录的按钮 btnSelectDir（请确保 activity_main.xml 中存在该控件）
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

    private fun resolveTreeUriToPath(uri: Uri): String? {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":")
        if (split.isNotEmpty() && "primary".equals(split[0], ignoreCase = true)) {
            val subPath = if (split.size > 1) split[1] else ""
            return File(Environment.getExternalStorageDirectory(), subPath).absolutePath
        }
        return null
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
                    e.printStackTrace()
                }
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
        }
    }
}
