package com.rapidfire.game.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.rapidfire.game.R
import com.rapidfire.game.audio.SoundManager
import com.rapidfire.game.databinding.FragmentGameBinding

class GameFragment : Fragment() {

    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!
    private var soundManager: SoundManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        soundManager = SoundManager(requireContext())
        binding.gameView.soundManager = soundManager

        binding.gameView.onGameOver = { stats ->
            binding.gameView.post {
                // Guard: fragment may have detached or already navigated away
                if (_binding != null && isAdded) {
                    try {
                        findNavController().navigate(
                            R.id.action_game_to_gameOver,
                            stats
                        )
                    } catch (_: IllegalArgumentException) {
                        // Already navigated — ignore
                    }
                }
            }
        }

        // Pause icon in HUD
        binding.gameView.onPauseRequested = { togglePause() }

        // Pause controls
        binding.btnResume.setOnClickListener {
            binding.pauseOverlay.visibility = View.GONE
            binding.gameView.resumeGame()
        }

        binding.btnRestart.setOnClickListener {
            binding.pauseOverlay.visibility = View.GONE
            binding.gameView.restartGame()
        }

        binding.btnQuit.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_game_to_menu)
            } catch (_: IllegalArgumentException) { }
        }
    }

    fun togglePause() {
        if (binding.pauseOverlay.visibility == View.VISIBLE) {
            binding.pauseOverlay.visibility = View.GONE
            binding.gameView.resumeGame()
        } else {
            binding.pauseOverlay.visibility = View.VISIBLE
            binding.gameView.pauseGame()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.gameView.pauseGame()
        binding.pauseOverlay.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        soundManager?.release()
        soundManager = null
        _binding = null
    }
}
