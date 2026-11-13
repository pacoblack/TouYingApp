package com.find.gang.file.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.find.gang.file.repository.TransferRepository
import com.find.gang.file.server.FileTransferStrategy
import com.find.gang.file.server.TextTransferStrategy
import com.find.gang.file.server.TransferServer
import com.find.gang.file.utils.ClipboardHelper
import com.find.gang.file.utils.NetworkUtils
import com.find.gang.file.utils.QrCodeUtils

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val port = 8080
    private val repository = TransferRepository(application)
    private var server: TransferServer? = null

    private val _serverUrl = MutableLiveData<String>()
    val serverUrl: LiveData<String> = _serverUrl

    private val _qrCodeBitmap = MutableLiveData<Bitmap>()
    val qrCodeBitmap: LiveData<Bitmap> = _qrCodeBitmap

    private val _fileLog = MutableLiveData("等待上传文件（请确保手机/电脑与投影仪连接同一 Wi-Fi）...")
    val fileLog: LiveData<String> = _fileLog

    // 支持 DataBinding 双向绑定的可变 LiveData
    val receivedText = MutableLiveData("")

    // 单次 UI 事件通知（Toast 提示与全选焦点触发）
    private val _toastEvent = MutableLiveData<String?>()
    val toastEvent: LiveData<String?> = _toastEvent

    private val _focusSelectEvent = MutableLiveData<Boolean>()
    val focusSelectEvent: LiveData<Boolean> = _focusSelectEvent

    init {
        initServer()
    }

    private fun initServer() {
        val ip = NetworkUtils.getLocalIpAddress()
        val url = "http://$ip:$port"
        _serverUrl.value = url
        _qrCodeBitmap.value = QrCodeUtils.generateQrCode(url, 500)

        // 组装策略列表
        val strategies = listOf(
            TextTransferStrategy { text ->
                receivedText.postValue(text)
                ClipboardHelper.copyText(getApplication(), text)
                _focusSelectEvent.postValue(true)
                _toastEvent.postValue("收到新文字并已自动复制")
            },
            FileTransferStrategy(repository) { fileName, savedPath ->
                val current = _fileLog.value ?: ""
                val prefix =
                    if (current.startsWith("等待上传")) "" else "\n----------------\n$current"
                _fileLog.postValue("✅ $fileName\n路径: $savedPath$prefix")
            }
        )

        server = TransferServer(port, getApplication(), repository, strategies).apply { start() }
    }

    fun onCopyClick() {
        val content = receivedText.value.orEmpty()
        if (content.isNotEmpty()) {
            ClipboardHelper.copyText(getApplication(), content)
            _focusSelectEvent.value = true
            _toastEvent.value = "已复制全部文字到剪贴板"
        }
    }

    fun onClearClick() {
        receivedText.value = ""
    }

    fun consumeToast() {
        _toastEvent.value = null
    }

    override fun onCleared() {
        super.onCleared()
        server?.stop()
    }
}
