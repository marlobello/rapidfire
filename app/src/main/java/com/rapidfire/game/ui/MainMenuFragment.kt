package com.rapidfire.game.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.rapidfire.game.BuildConfig
import com.rapidfire.game.R
import com.rapidfire.game.databinding.FragmentMainMenuBinding
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

        binding.btnStartGame.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_game)
        }

        binding.btnLeaderboard.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_leaderboard)
        }

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_menu_to_settings)
        }

        checkForUpdates()
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
                        onError = { msg ->
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
