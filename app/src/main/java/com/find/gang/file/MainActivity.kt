package com.find.gang.file

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.databinding.DataBindingUtil
import com.find.gang.file.databinding.ActivityMainBinding
import com.find.gang.file.ui.BottomNavCoordinator
import com.find.gang.file.ui.NavTab
import com.find.gang.file.viewmodel.TransferViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: TransferViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var navCoordinator: BottomNavCoordinator

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showToast("未授予存储权限，文件接收功能可能受限")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkStoragePermission()

        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main).apply {
            lifecycleOwner = this@MainActivity
            vm = viewModel
        }

        initNavigation(savedInstanceState)
        observeGlobalEvents()
    }

    private fun initNavigation(savedInstanceState: Bundle?) {
        navCoordinator = BottomNavCoordinator(
            fragmentManager = supportFragmentManager,
            containerId = R.id.fragmentContainer,
            tabViews = mapOf(
                NavTab.HOME to binding.tabHome,
                NavTab.BROWSER to binding.tabBrowser,
                NavTab.VIDEO to binding.tabVideo
            )
        )

        val initialTab = savedInstanceState?.getString("CURRENT_TAB")
            ?.let { runCatching { NavTab.valueOf(it) }.getOrNull() }
            ?: NavTab.HOME

        navCoordinator.switchTo(initialTab)
    }

    private fun observeGlobalEvents() {
        viewModel.toastEvent.observe(this) { msg ->
            msg?.let {
                showToast(it)
                viewModel.consumeToast()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        navCoordinator.currentTab?.let { outState.putString("CURRENT_TAB", it.name) }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            "package:$packageName".toUri()
                        )
                    )
                }.onFailure {
                    runCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }
            }
        } else if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
