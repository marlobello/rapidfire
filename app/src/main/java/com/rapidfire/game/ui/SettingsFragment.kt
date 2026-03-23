package com.rapidfire.game.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.rapidfire.game.BuildConfig
import com.rapidfire.game.R
import com.rapidfire.game.databinding.FragmentSettingsBinding
import com.rapidfire.game.update.AppUpdater
import com.rapidfire.game.update.UpdateInfo
import com.rapidfire.game.update.UpdateResult
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var pendingUpdate: UpdateInfo? = null
    private var updater: AppUpdater? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val prefs = requireContext().getSharedPreferences("rapidfire_prefs", Context.MODE_PRIVATE)

        binding.switchSound.isChecked = prefs.getBoolean("sound_enabled", true)
        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sound_enabled", isChecked).apply()
        }

        binding.tvAbout.text = getString(R.string.about_text, BuildConfig.VERSION_NAME)

        binding.tvGithub.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/marlobello/rapidfire")))
        }
        binding.tvLicense.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/marlobello/rapidfire/blob/main/LICENSE")))
        }

        binding.btnCheckUpdates.setOnClickListener {
            if (pendingUpdate != null) {
                startDownload()
            } else {
                checkForUpdates()
            }
        }
    }

    private fun checkForUpdates() {
        val btn = binding.btnCheckUpdates
        btn.isEnabled = false
        btn.text = getString(R.string.checking_for_updates)
        setStatus(null)

        updater = AppUpdater(requireContext().applicationContext)

        lifecycleScope.launch {
            val result = updater!!.checkForUpdate(force = true)

            if (_binding == null) return@launch

            when (result) {
                is UpdateResult.Available -> {
                    pendingUpdate = result.info
                    btn.isEnabled = true
                    btn.text = getString(R.string.update_now)
                    setStatus(getString(
                        R.string.update_available_message,
                        result.info.versionName,
                        BuildConfig.VERSION_NAME,
                        result.info.releaseNotes
                    ))
                }
                is UpdateResult.UpToDate -> {
                    btn.isEnabled = true
                    btn.text = getString(R.string.check_for_updates)
                    setStatus(getString(R.string.no_update_available))
                }
                is UpdateResult.Error -> {
                    btn.isEnabled = true
                    btn.text = getString(R.string.update_retry)
                    setStatus(getString(R.string.update_check_failed))
                }
                is UpdateResult.Throttled -> {
                    btn.isEnabled = true
                    btn.text = getString(R.string.check_for_updates)
                    setStatus(getString(R.string.no_update_available))
                }
            }
        }
    }

    private fun startDownload() {
        val info = pendingUpdate ?: return
        val u = updater ?: return

        binding.btnCheckUpdates.isEnabled = false
        binding.btnCheckUpdates.text = getString(R.string.downloading)
        setStatus(getString(R.string.download_started))

        u.downloadAndInstall(
            info,
            onDownloadStarted = {},
            onDownloadComplete = {
                if (_binding != null) {
                    binding.btnCheckUpdates.text = getString(R.string.download_complete)
                    setStatus(getString(R.string.tap_notification_to_install))
                }
            },
            onError = { _ ->
                if (_binding != null) {
                    binding.btnCheckUpdates.isEnabled = true
                    binding.btnCheckUpdates.text = getString(R.string.update_retry)
                    setStatus(getString(R.string.download_failed))
                }
            }
        )
    }

    private fun setStatus(text: String?) {
        if (text == null) {
            binding.tvUpdateStatus.visibility = View.GONE
        } else {
            binding.tvUpdateStatus.text = text
            binding.tvUpdateStatus.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        updater = null
        pendingUpdate = null
    }
}
