package com.zhengui.waterreminder.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zhengui.waterreminder.BuildConfig
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.ActivityAboutBinding
import com.zhengui.waterreminder.util.UpdateManager
import kotlinx.coroutines.launch

class AboutActivity : AppCompatActivity() {

    private var _binding: ActivityAboutBinding? = null
    private val binding get() = _binding!!
    private var pendingUpdateInfo: UpdateManager.UpdateInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToolbarNav.setOnClickListener { finish() }
        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME}"

        binding.btnFeatures.setOnClickListener { showInfoDialog("功能介绍", getString(R.string.about_features)) }
        binding.btnVersionHistory.setOnClickListener { showInfoDialog("版本历史", getString(R.string.about_version_history)) }
        binding.btnPrivacy.setOnClickListener { showInfoDialog("隐私政策", getPrivacyText()) }
        binding.btnDeveloper.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/52uni/heleme"))
            startActivity(intent)
        }

        // 进入页面后静默检查更新
        checkUpdateSilently()
    }

    private fun getPrivacyText(): String {
        return buildString {
            appendLine(getString(R.string.privacy_subtitle))
            appendLine()
            appendLine(getString(R.string.privacy_intro))
            appendLine()
            appendLine("01  ${getString(R.string.privacy_item_1)}")
            appendLine("02  ${getString(R.string.privacy_item_2)}")
            appendLine("03  ${getString(R.string.privacy_item_3)}")
            appendLine("04  ${getString(R.string.privacy_item_4)}")
            appendLine("05  ${getString(R.string.privacy_item_5)}")
            appendLine()
            appendLine(getString(R.string.privacy_conclusion))
            appendLine()
            appendLine(getString(R.string.privacy_footnote))
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun checkUpdateSilently() {
        lifecycleScope.launch {
            val info = UpdateManager.checkForUpdate()
            if (info != null) {
                pendingUpdateInfo = info
                binding.updateHint.visibility = View.VISIBLE
                binding.tvUpdateHint.text = getString(R.string.update_available_hint, info.tagName)
                binding.updateHint.setOnClickListener { showUpdateDialog() }
            }
        }
    }

    private fun showUpdateDialog() {
        val info = pendingUpdateInfo ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_new_version))
            .setMessage("v${info.tagName}\n\n${info.body}")
            .setPositiveButton(getString(R.string.update_now)) { _, _ ->
                lifecycleScope.launch {
                    UpdateManager.downloadAndInstall(this@AboutActivity, info)
                }
            }
            .setNegativeButton(getString(R.string.update_later), null)
            .show()
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}
