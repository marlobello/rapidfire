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
import com.rapidfire.game.model.GameMode

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

        // Set game mode before the game starts
        val modeName = arguments?.getString("gameMode") ?: "CLASSIC"
        val gameMode = GameMode.fromName(modeName)
        binding.gameView.gameState.gameMode = gameMode

        // Capture the view reference so the game thread closure doesn't touch binding
        val gameView = binding.gameView

        gameView.onGameOver = { stats ->
            stats.putString("gameMode", modeName)
            gameView.post {
                if (_binding != null && isAdded) {
                    try {
                        findNavController().navigate(
                            R.id.action_game_to_gameOver,
                            stats
                        )
                    } catch (_: Exception) {
                        // Already navigated or fragment detached
                    }
                }
            }
        }

        // Pause icon in HUD
        gameView.onPauseRequested = { togglePause() }

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
            } catch (_: Exception) { }
        }
    }

    fun togglePause() {
        val b = _binding ?: return
        if (b.pauseOverlay.visibility == View.VISIBLE) {
            b.pauseOverlay.visibility = View.GONE
            b.gameView.resumeGame()
        } else {
            b.pauseOverlay.visibility = View.VISIBLE
            b.gameView.pauseGame()
        }
    }

    override fun onPause() {
        super.onPause()
        val b = _binding ?: return
        b.gameView.pauseGame()
        b.pauseOverlay.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        // Null out callbacks before destroying binding to prevent game thread
        // from invoking them after the view is gone
        _binding?.gameView?.onGameOver = null
        _binding?.gameView?.onPauseRequested = null
        // Clear sound manager on GameView BEFORE releasing it — prevents
        // the game thread from calling play*() on a released SoundManager
        _binding?.gameView?.soundManager = null
        soundManager?.release()
        soundManager = null
        _binding = null
        super.onDestroyView()
    }
}
