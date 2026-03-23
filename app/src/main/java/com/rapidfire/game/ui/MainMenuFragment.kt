package com.rapidfire.game.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rapidfire.game.R
import com.rapidfire.game.databinding.FragmentMainMenuBinding
import com.rapidfire.game.model.GameMode
import com.rapidfire.game.update.AppUpdater
import com.rapidfire.game.update.UpdateResult
import kotlinx.coroutines.launch

class MainMenuFragment : Fragment() {

    private var _binding: FragmentMainMenuBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMainMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnStartGame.setOnClickListener { showModeSelection() }

        binding.btnLeaderboard.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_leaderboard)
        }

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_settings)
        }

        checkForUpdates()
    }

    private fun showModeSelection() {
        val modes = arrayOf(
            GameMode.CLASSIC,
            GameMode.BOSS_RUSH,
            GameMode.QUICK_START_50,
            GameMode.QUICK_START_100
        )
        val labels = arrayOf(
            getString(R.string.mode_classic_label),
            getString(R.string.mode_boss_rush_label),
            getString(R.string.mode_quick_start_50_label),
            getString(R.string.mode_quick_start_100_label)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.select_game_mode)
            .setItems(labels) { _, which ->
                startGame(modes[which])
            }
            .show()
    }

    private fun startGame(mode: GameMode) {
        val bundle = Bundle().apply { putString("gameMode", mode.name) }
        try {
            findNavController().navigate(R.id.action_menu_to_game, bundle)
        } catch (_: Exception) { }
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            val updater = AppUpdater(requireContext().applicationContext)
            val result = updater.checkForUpdate(force = false)

            if (_binding == null || !isAdded) return@launch

            if (result is UpdateResult.Available) {
                val info = result.info
                binding.updateBanner.visibility = View.VISIBLE
                binding.tvUpdateVersion.text = getString(
                    R.string.update_banner_text,
                    info.versionName
                )
                binding.btnUpdate.setOnClickListener {
                    binding.btnUpdate.isEnabled = false
                    binding.btnUpdate.text = getString(R.string.downloading)
                    updater.downloadAndInstall(
                        info,
                        onDownloadStarted = {},
                        onDownloadComplete = {
                            if (_binding != null) {
                                binding.btnUpdate.text = getString(R.string.download_complete)
                            }
                        },
                        onError = { _ ->
                            if (_binding != null) {
                                binding.btnUpdate.isEnabled = true
                                binding.btnUpdate.text = getString(R.string.update_retry)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
