package com.find.gang.file

import android.app.Application
import com.tencent.mmkv.MMKV

class MyApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化 MMKV，默认根目录为 files/mmkv
        MMKV.initialize(this)
    }
}