package com.rapidfire.game.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.rapidfire.game.R
import com.rapidfire.game.BuildConfig
import com.rapidfire.game.data.ScoreDatabase
import com.rapidfire.game.data.ScoreEntity
import com.rapidfire.game.databinding.FragmentGameOverBinding
import com.rapidfire.game.model.GameMode
import kotlinx.coroutines.launch

class GameOverFragment : Fragment() {

    private var _binding: FragmentGameOverBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGameOverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val round = arguments?.getInt("round", 0) ?: 0
        val score = arguments?.getInt("score", 0) ?: 0
        val bricksDestroyed = arguments?.getInt("bricksDestroyed", 0) ?: 0
        val boardClears = arguments?.getInt("boardClears", 0) ?: 0
        val mulligansUsed = arguments?.getInt("mulligansUsed", 0) ?: 0
        val shotsFired = arguments?.getInt("shotsFired", 0) ?: 0
        val gameModeName = arguments?.getString("gameMode") ?: "CLASSIC"
        val gameMode = GameMode.fromName(gameModeName)

        // Display stats
        binding.tvScore.text = "%,d".format(score)
        binding.tvRound.text = "Round $round"
        binding.tvBricksDestroyed.text = "$bricksDestroyed"
        binding.tvBoardClears.text = "$boardClears"
        binding.tvShotsFired.text = "$shotsFired"
        binding.tvMulligansUsed.text = "$mulligansUsed"

        // Save score and check for high score
        if (round > 0) {
            lifecycleScope.launch {
                try {
                    val ctx = requireContext().applicationContext
                    val (previousHigh, _) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val db = ScoreDatabase.getInstance(ctx)
                        val high = db.scoreDao().getHighScore() ?: 0
                        db.scoreDao().insertScore(
                            ScoreEntity(
                                score = score,
                                round = round,
                                bricksDestroyed = bricksDestroyed,
                                boardClears = boardClears,
                                mulligansUsed = mulligansUsed,
                                shotsFired = shotsFired
                            )
                        )
                        high to Unit
                    }

                    if (score > previousHigh && score > 0 && _binding != null) {
                        binding.tvNewHighScore.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        android.util.Log.e("GameOver", "Failed to save score", e)
                    }
                }
            }
        }

        binding.btnPlayAgain.setOnClickListener {
            try {
                val bundle = android.os.Bundle().apply {
                    putString("gameMode", gameModeName)
                }
                findNavController().navigate(R.id.action_gameOver_to_game, bundle)
            } catch (_: IllegalArgumentException) { }
        }

        binding.btnMainMenu.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_gameOver_to_menu)
            } catch (_: IllegalArgumentException) { }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
