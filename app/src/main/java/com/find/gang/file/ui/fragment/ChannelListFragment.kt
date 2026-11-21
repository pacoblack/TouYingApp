package com.find.gang.file.ui.fragment

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
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
 * IPTV 频道播放列表页面（继承 BaseTvFragment 统一管理 DataBinding 与 TV 遥控器焦点）
 */
class ChannelListFragment : BaseTvFragment<FragmentChannelListBinding>() {

    private var groups: List<TvChannelGroup> = emptyList()
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
                channelAdapter.submitList(groups[checkedId].channels)
                binding.rvChannels.scrollToPosition(0)
            }
        }
    }

    override fun observeData() {
        loadPlaylist()
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

            binding.rgGenres.removeAllViews()
            groups.forEachIndexed { index, group ->
                val radioButton = RadioButton(requireContext()).apply {
                    id = index
                    text = "${group.genre} (${group.channels.size})"
                    buttonDrawable = null
                    setPadding(32, 16, 32, 16)
                    isFocusable = true
                    isFocusableInTouchMode = false
                    setOnFocusChangeListener { v, hasFocus ->
                        v.setBackgroundColor(
                            if (hasFocus) Color.parseColor("#331976D2") else Color.TRANSPARENT
                        )
                    }
                    setOnCheckedChangeListener { buttonView, isChecked ->
                        buttonView.setTextColor(
                            if (isChecked) Color.parseColor("#1976D2") else Color.DKGRAY
                        )
                        buttonView.paint.isFakeBoldText = isChecked
                    }
                }
                binding.rgGenres.addView(radioButton)
            }

            // 默认选中第一个分类并聚焦到频道列表，方便遥控器直接上下选台
            (binding.rgGenres.getChildAt(0) as? RadioButton)?.isChecked = true
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
                    if (hasFocus) Color.parseColor("#221976D2") else Color.TRANSPARENT
                )
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.name
            holder.subtitle.text = if (item.urls.size > 1) {
                "共 ${item.urls.size} 条线路 · ${item.primaryUrl}"
            } else {
                item.primaryUrl
            }
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(android.R.id.text1)
            val subtitle: TextView = view.findViewById(android.R.id.text2)
        }
    }
}
