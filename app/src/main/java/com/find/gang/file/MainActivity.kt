package com.find.gang.file

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

class MainActivity : AppCompatActivity() {

    private var server: TransferServer? = null
    private val port = 8080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ivQrCode = findViewById<ImageView>(R.id.ivQrCode)
        val tvServerUrl = findViewById<TextView>(R.id.tvServerUrl)
        val tvLog = findViewById<TextView>(R.id.tvLog)

        val ip = getLocalIpAddress()
        val url = "http://$ip:$port"
        tvServerUrl.text = url
        ivQrCode.setImageBitmap(generateQrCode(url, 500))

        // 启动 HTTP 服务
        server = TransferServer(
            port = port,
            context = this,
            onTextReceived = { text ->
                runOnUiThread {
                    copyToClipboard(text)
                    tvLog.text = buildString {
                        append("【收到文字 · 已复制】\n")
                        append(text)
                        append("\n\n----------------\n")
                        append(tvLog.text)
                    }
                }
            },
            onFileReceived = { fileName, savedPath ->
                runOnUiThread {
                    tvLog.text = buildString {
                        append("【收到文件】")
                        append(fileName)
                        append("\n保存路径: ")
                        append(savedPath)
                        append("\n\n----------------\n")
                        append(tvLog.text)
                    }
                }
            }
        ).apply { start() }
    }

    // 遍历网卡获取局域网 IPv4 地址（兼容 Wi-Fi 与有线网口）
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "未连接局域网"
    }

    // 使用 ZXing 生成二维码 Bitmap
    private fun generateQrCode(content: String, size: Int): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return bitmap
    }

    // 自动复制收到的文字到投影仪剪贴板
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("TVTransfer", text))
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }
}
