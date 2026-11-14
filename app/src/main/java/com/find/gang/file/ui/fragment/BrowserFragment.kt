package com.find.gang.file.ui.fragment

import android.view.LayoutInflater
import android.view.ViewGroup
import com.find.gang.file.R
import com.find.gang.file.databinding.FragmentBrowserBinding
import com.find.gang.file.ui.BaseTvFragment

class BrowserFragment : BaseTvFragment<FragmentBrowserBinding>() {

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentBrowserBinding = FragmentBrowserBinding.inflate(inflater, container, false)

    override fun getDefaultFocusViewId(): Int = R.id.etBrowserUrl // 替换为实际默认焦点控件 ID

    override fun initViews() {
        // 初始化 WebView 等设置
    }
}
