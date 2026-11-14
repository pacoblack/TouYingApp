package com.find.gang.file.ui.fragment

import android.content.Intent
import android.os.Environment
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.find.gang.file.R
import com.find.gang.file.databinding.FragmentHomeTransferBinding
import com.find.gang.file.repository.TransferRepository
import com.find.gang.file.ui.BaseTvFragment
import com.find.gang.file.utils.DirectoryOpenChainFactory
import com.find.gang.file.utils.TvDirectoryDialogs
import com.find.gang.file.utils.TvSystemFacade

class HomeTransferFragment : BaseTvFragment<FragmentHomeTransferBinding>() {

    private lateinit var repository: TransferRepository
    private var currentDirPath: String = ""
    private val serverPort = 8080

    private val selectDirLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val ctx = requireContext()
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val realPath = TvSystemFacade.resolveTreeUriToPath(it)
            if (realPath != null) applyNewDirectory(realPath)
            else showToast("暂不支持外置SD卡，请选择内部存储目录")
        }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomeTransferBinding {
        return FragmentHomeTransferBinding.inflate(inflater, container, false).apply {
            vm = sharedViewModel
        }
    }

    override fun getDefaultFocusViewId(): Int = R.id.btnOpenDir

    override fun initViews() {
        repository = TransferRepository(requireContext())
        binding.tvFileLog.movementMethod = ScrollingMovementMethod()

        val savedDir = repository.getBaseRootDir().absolutePath
        val defaultDir = savedDir.ifEmpty {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        }
        updateDirDisplay(defaultDir)
        initQrCode()
    }

    override fun initListeners() {
        val ctx = requireContext()
        binding.btnOpenDir.setOnClickListener {
            DirectoryOpenChainFactory.open(ctx, currentDirPath) { dirFile ->
                TvDirectoryDialogs.showBuiltInFileBrowser(ctx, dirFile)
            }
        }

        binding.btnSelectDir.setOnClickListener {
            TvDirectoryDialogs.showSelectDirectoryDialog(
                context = ctx,
                onDirectorySelected = ::applyNewDirectory,
                onRequestSystemPicker = {
                    runCatching { selectDirLauncher.launch(null) }
                        .onFailure { showToast("当前投影仪不支持原生选择器，请选择预设目录") }
                }
            )
        }

        binding.btnCopyText.setOnClickListener {
            val content = binding.etReceivedText.text?.toString()?.trim().orEmpty()
            if (TvSystemFacade.copyToClipboard(ctx, content)) {
                binding.etReceivedText.selectAll()
                showToast("已成功复制到剪贴板！")
            } else {
                showToast("当前没有可复制的文本内容")
            }
        }
    }

    override fun observeData() {
        sharedViewModel.focusSelectEvent.observe(viewLifecycleOwner) { shouldSelect ->
            if (shouldSelect == true) binding.btnCopyText.requestFocus()
        }
    }

    private fun initQrCode() {
        val ip = TvSystemFacade.getLocalIpAddress()
        if (ip != null) {
            val url = "http://$ip:$serverPort"
            binding.tvServerUrl.text = "手机扫码或浏览器访问：$url"
            TvSystemFacade.generateQrCode(url)?.let { binding.ivQrCode.setImageBitmap(it) }
        } else {
            binding.tvServerUrl.text = "未检测到局域网 Wi-Fi，请先连接网络"
        }
    }

    private fun applyNewDirectory(realPath: String) {
        repository.setCustomRootDir(realPath)
        updateDirDisplay(realPath)
        showToast("存储目录已更新为: $realPath")
    }

    private fun updateDirDisplay(path: String) {
        currentDirPath = path
        binding.tvCurrentDir.text = "当前保存目录：$path"
    }
}
