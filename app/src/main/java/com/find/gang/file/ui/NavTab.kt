package com.find.gang.file.ui

import androidx.annotation.IdRes
import com.find.gang.file.R
import com.find.gang.file.ui.fragment.BrowserFragment
import com.find.gang.file.ui.fragment.ChannelListFragment
import com.find.gang.file.ui.fragment.HomeTransferFragment
import com.find.gang.file.ui.fragment.VideoFragment

enum class NavTab(
    val tag: String,
    @IdRes val buttonId: Int
) {
//    HOME(tag = "TAB_HOME", buttonId = R.id.tabHome) {
//        override fun createFragment(): BaseTvFragment<*> = HomeTransferFragment()
//    },
    BROWSER(tag = "TAB_BROWSER", buttonId = R.id.tabBrowser) {
        override fun createFragment(): BaseTvFragment<*> = ChannelListFragment()
    },
    VIDEO(tag = "TAB_VIDEO", buttonId = R.id.tabVideo) {
        override fun createFragment(): BaseTvFragment<*> = VideoFragment()
    };

    abstract fun createFragment(): BaseTvFragment<*>
}
