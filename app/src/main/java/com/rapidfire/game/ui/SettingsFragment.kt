package com.rapidfire.game.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rapidfire.game.BuildConfig
import com.rapidfire.game.R
import com.rapidfire.game.databinding.FragmentSettingsBinding
import com.rapidfire.game.update.AppUpdater
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

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

        binding.btnCheckUpdates.setOnClickListener {
            checkForUpdates(manual = true)
        }
    }

    private fun checkForUpdates(manual: Boolean) {
        val btn = binding.btnCheckUpdates
        btn.isEnabled = false
        btn.text = getString(R.string.checking_for_updates)

        lifecycleScope.launch {
            val updater = AppUpdater(requireContext())
            val update = updater.checkForUpdate(force = manual)

            if (_binding == null) return@launch

            btn.isEnabled = true
            btn.text = getString(R.string.check_for_updates)

            if (update != null) {
                showUpdateDialog(updater, update)
            } else if (manual) {
                Toast.makeText(requireContext(), R.string.no_update_available, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateDialog(updater: AppUpdater, update: com.rapidfire.game.update.UpdateInfo) {
        if (_binding == null || !isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.update_available_title)
            .setMessage(getString(R.string.update_available_message, update.versionName, BuildConfig.VERSION_NAME, update.releaseNotes))
            .setPositiveButton(R.string.update_now) { _, _ ->
                updater.downloadUpdate(update)
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
