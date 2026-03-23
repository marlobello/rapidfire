package com.rapidfire.game.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rapidfire.game.BuildConfig
import com.rapidfire.game.R
import com.rapidfire.game.databinding.FragmentMainMenuBinding
import com.rapidfire.game.update.AppUpdater
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

        // Auto-check for updates (respects 6-hour throttle)
        checkForUpdates()
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            val updater = AppUpdater(requireContext())
            val update = updater.checkForUpdate(force = false)

            if (_binding == null || !isAdded || update == null) return@launch

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.update_available_title)
                .setMessage(
                    getString(
                        R.string.update_available_message,
                        update.versionName,
                        BuildConfig.VERSION_NAME,
                        update.releaseNotes
                    )
                )
                .setPositiveButton(R.string.update_now) { _, _ ->
                    updater.openReleasePage(update)
                }
                .setNegativeButton(R.string.later, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
