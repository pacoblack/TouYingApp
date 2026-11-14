package com.find.gang.file.ui.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import com.find.gang.file.R
import com.find.gang.file.databinding.FragmentVideoBinding
import com.find.gang.file.ui.BaseTvFragment

class VideoFragment : BaseTvFragment<FragmentVideoBinding>() {

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentVideoBinding = FragmentVideoBinding.inflate(inflater, container, false)

    override fun getDefaultFocusViewId(): Int = R.id.rvVideoList // 替换为实际默认焦点控件 ID

    override fun initViews() {
        // 初始化在线视频列表
    }
}
