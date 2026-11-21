package com.find.gang.file.ui.fragment

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.find.gang.file.R
import com.find.gang.file.databinding.FragmentChannelListBinding
import com.find.gang.file.media.IptvPlaylistParser
import com.find.gang.file.media.TvChannel
import com.find.gang.file.media.TvChannelGroup
import com.find.gang.file.ui.BaseTvFragment
import kotlinx.coroutines.launch

/**
 * IPTV 频道播放列表页面（保留分类 Tab 选中状态与缓存列表，返回时不重置）
 */
class ChannelListFragment : BaseTvFragment<FragmentChannelListBinding>() {

    // Fragment 实例级缓存：返回栈弹出重建视图时依然保留数据与选中索引
    private var groups: List<TvChannelGroup> = emptyList()
    private var selectedGenreIndex: Int = 0

    private val channelAdapter = ChannelAdapter { channel ->
        openVideoPlayer(channel)
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentChannelListBinding {
        return FragmentChannelListBinding.inflate(inflater, container, false)
    }

    override fun getDefaultFocusViewId(): Int = R.id.rvChannels

    override fun initViews() {
        binding.rvChannels.apply {
            layoutManager = LinearLayoutManager(requireContext())
            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
            adapter = channelAdapter
        }
    }

    override fun initListeners() {
        binding.rgGenres.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId in groups.indices) {
                val isChanged = selectedGenreIndex != checkedId
                selectedGenreIndex = checkedId
                channelAdapter.submitList(groups[checkedId].channels)
                // 仅在手动切换新 Tab 时回到顶部；从播放页返回恢复视图时保留原有滚动位置
                if (isChanged) {
                    binding.rvChannels.scrollToPosition(0)
                }
            }
        }
    }

    override fun observeData() {
        if (groups.isNotEmpty()) {
            // 已有内存缓存（例如从 VideoFragment 返回），直接恢复界面与选中 Tab，不再重新请求网络
            binding.progressLoading.visibility = View.GONE
            renderGenreTabs(isRestore = true)
        } else {
            loadPlaylist()
        }
    }

    private fun loadPlaylist() {
        binding.progressLoading.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            groups = IptvPlaylistParser.fetchAndParse("https://live.zbds.top/tv/iptv4.txt")
            binding.progressLoading.visibility = View.GONE

            if (groups.isEmpty()) {
                showToast("播放列表加载失败，请检查网络")
                return@launch
            }
            renderGenreTabs(isRestore = false)
        }
    }

    /** 渲染顶部分类标签并恢复之前选中的分类 */
    private fun renderGenreTabs(isRestore: Boolean) {
        binding.rgGenres.removeAllViews()
        groups.forEachIndexed { index, group ->
            val radioButton = RadioButton(requireContext()).apply {
                id = index
                text = buildString {
                    append(group.genre)
                    append(" (")
                    append(group.channels.size)
                    append(")")
                }
                buttonDrawable = null
                setPadding(32, 16, 32, 16)
                isFocusable = true
                isFocusableInTouchMode = false
                setOnFocusChangeListener { v, hasFocus ->
                    v.setBackgroundColor(
                        if (hasFocus) "#331976D2".toColorInt() else Color.TRANSPARENT
                    )
                }
                setOnCheckedChangeListener { buttonView, isChecked ->
                    buttonView.setTextColor(
                        if (isChecked) "#1976D2".toColorInt() else Color.DKGRAY
                    )
                    buttonView.paint.isFakeBoldText = isChecked
                }
            }
            binding.rgGenres.addView(radioButton)
        }

        val targetIndex = selectedGenreIndex.coerceIn(0, groups.lastIndex)
        val targetTab = binding.rgGenres.getChildAt(targetIndex) as? RadioButton
        targetTab?.isChecked = true

        // 确保顶部分类栏自动横向滚动到选中的 Tab 位置（防止卫视/地方频道等靠后的 Tab 跑出屏幕外）
        targetTab?.post {
            val scrollX = targetTab.left - (binding.scrollGenres.width - targetTab.width) / 2
            binding.scrollGenres.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }

        if (!isRestore) {
            binding.rvChannels.post {
                requestDefaultFocus()
            }
        }
    }

    /** 点击频道：打开 VideoFragment 并自动播放 */
    private fun openVideoPlayer(channel: TvChannel) {
        val videoFragment = VideoFragment.newInstance(channel, autoPlay = true)
        parentFragmentManager.beginTransaction()
            .replace(id, videoFragment)
            .addToBackStack("video_player")
            .commit()
    }

    private class ChannelAdapter(
        private val onClick: (TvChannel) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.ViewHolder>() {

        private var items: List<TvChannel> = emptyList()

        fun submitList(newList: List<TvChannel>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            view.isFocusable = true
            view.setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundColor(
                    if (hasFocus) "#221976D2".toColorInt() else Color.TRANSPARENT
                )
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.name
            holder.subtitle.text = ""
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(android.R.id.text1)
            val subtitle: TextView = view.findViewById(android.R.id.text2)
        }
    }
}
