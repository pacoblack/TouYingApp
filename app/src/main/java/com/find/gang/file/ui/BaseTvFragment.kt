package com.find.gang.file.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.find.gang.file.viewmodel.TransferViewModel

/**
 * 模板方法模式（Template Method Pattern）：
 * 统一封装 DataBinding 生命周期、共享 ViewModel、Toast 提示以及 TV 遥控器默认焦点分配。
 */
abstract class BaseTvFragment<VB : ViewDataBinding> : Fragment() {

    private var _binding: VB? = null
    protected val binding: VB
        get() = _binding ?: throw IllegalStateException("Binding 仅在 onCreateView 与 onDestroyView 之间有效")

    // 所有子 Fragment 共享宿主 Activity 的 TransferViewModel
    protected val sharedViewModel: TransferViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = createBinding(inflater, container).apply {
            lifecycleOwner = viewLifecycleOwner
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 按固定模板顺序执行子类初始化逻辑
        initViews()
        initListeners()
        observeData()

        // 页面首次展示时自动获取焦点
        if (savedInstanceState == null) {
            requestDefaultFocus()
        }
    }

    /**
     * 子类提供具体的 DataBinding 实例
     */
    protected abstract fun createBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    /**
     * 子类指定页面默认的 TV 遥控器初始聚焦控件 ID（返回 0 表示不自动聚焦）
     */
    @IdRes
    protected open fun getDefaultFocusViewId(): Int = 0

    /**
     * 模板步骤 1：初始化视图状态
     */
    protected open fun initViews() {}

    /**
     * 模板步骤 2：绑定点击与按键监听
     */
    protected open fun initListeners() {}

    /**
     * 模板步骤 3：订阅 ViewModel 数据变化
     */
    protected open fun observeData() {}

    /**
     * 供外部导航协调器（如底部栏按遥控器“上键”）或自身调用，聚焦到当前页面的默认控件
     */
    fun requestDefaultFocus(): Boolean {
        val focusId = getDefaultFocusViewId()
        if (focusId == 0 || _binding == null) return false
        return binding.root.findViewById<View>(focusId)?.requestFocus() == true
    }

    protected fun showToast(msg: String) {
        context?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // 防止 Fragment 视图销毁后发生内存泄漏
    }
}
