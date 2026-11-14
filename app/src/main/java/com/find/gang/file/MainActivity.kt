package com.find.gang.file

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.databinding.DataBindingUtil
import com.find.gang.file.databinding.ActivityMainBinding
import com.find.gang.file.repository.TransferRepository
import com.find.gang.file.utils.DirectoryOpenChainFactory
import com.find.gang.file.utils.TvDirectoryDialogs
import com.find.gang.file.utils.TvSystemFacade
import com.find.gang.file.viewmodel.TransferViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: TransferViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: TransferRepository

    private var currentDirPath: String = ""
    private val serverPort = 8080

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showToast("未授予存储权限，文件接收功能可能受限")
    }

    private val selectDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val realPath = TvSystemFacade.resolveTreeUriToPath(it)
            if (realPath != null) applyNewDirectory(realPath)
            else showToast("暂不支持外置SD卡，请选择内部存储目录")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TransferRepository(this)
        checkStoragePermission()

        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main).apply {
            lifecycleOwner = this@MainActivity
            vm = viewModel
        }

        initViews()
        initQrCode()
        initListeners()
        observeViewModel()
    }

    private fun initViews() {
        binding.tvFileLog.movementMethod = ScrollingMovementMethod()
        val defaultDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        updateDirDisplay(defaultDir)

        binding.tabHome.isSelected = true
        binding.btnOpenDir.requestFocus()
    }

    private fun initQrCode() {
        val ip = TvSystemFacade.getLocalIpAddress()
        if (ip != null) {
            val url = "http://$ip:$serverPort"
            binding.tvServerUrl.text = buildString {
                append("手机扫码或浏览器访问：")
                append(url)
            }
            TvSystemFacade.generateQrCode(url)?.let { binding.ivQrCode.setImageBitmap(it) }
        } else {
            binding.tvServerUrl.text = buildString {
                append("未检测到局域网 Wi-Fi，请先连接网络")
            }
        }
    }

    private fun initListeners() {
        // 1. 打开目录（触发责任链模式）
        binding.btnOpenDir.setOnClickListener {
            DirectoryOpenChainFactory.open(this, currentDirPath) { dirFile ->
                TvDirectoryDialogs.showBuiltInFileBrowser(this, dirFile)
            }
        }

        // 2. 修改目录
        binding.btnSelectDir.setOnClickListener {
            TvDirectoryDialogs.showSelectDirectoryDialog(
                context = this,
                onDirectorySelected = ::applyNewDirectory,
                onRequestSystemPicker = {
                    runCatching { selectDirLauncher.launch(null) }
                        .onFailure { showToast("当前投影仪不支持原生选择器，请选择预设目录") }
                }
            )
        }

        // 3. 一键复制
        binding.btnCopyText.setOnClickListener {
            val content = binding.etReceivedText.text?.toString()?.trim().orEmpty()
            if (TvSystemFacade.copyToClipboard(this, content)) {
                binding.etReceivedText.selectAll()
                showToast("已成功复制到剪贴板！")
            } else {
                showToast("当前没有可复制的文本内容")
            }
        }
    }

    private fun observeViewModel() {
        viewModel.toastEvent.observe(this) { msg ->
            msg?.let {
                showToast(it)
                viewModel.consumeToast()
            }
        }
        viewModel.focusSelectEvent.observe(this) { shouldSelect ->
            if (shouldSelect == true) binding.btnCopyText.requestFocus()
        }
    }

    private fun applyNewDirectory(realPath: String) {
        repository.setCustomRootDir(realPath)
        updateDirDisplay(realPath)
        showToast("存储目录已更新为: $realPath")
    }

    private fun updateDirDisplay(path: String) {
        currentDirPath = path
        binding.tvCurrentDir.text = buildString {
            append("当前保存目录：")
            append(path)
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri()))
                }.onFailure {
                    runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
