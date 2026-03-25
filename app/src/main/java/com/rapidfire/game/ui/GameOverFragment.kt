package com.rapidfire.game.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.rapidfire.game.R
import com.rapidfire.game.data.ScoreDatabase
import com.rapidfire.game.data.ScoreEntity
import com.rapidfire.game.databinding.FragmentGameOverBinding
import com.rapidfire.game.model.GameMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        binding.tvRound.text = "$round"
        binding.tvBricksDestroyed.text = "%,d".format(bricksDestroyed)
        binding.tvBoardClears.text = "$boardClears"
        binding.tvShotsFired.text = "%,d".format(shotsFired)
        binding.tvMulligansUsed.text = "$mulligansUsed"

        // Set leaderboard title based on category
        val isBossRush = gameMode == GameMode.BOSS_RUSH
        binding.tvLeaderboardTitle.text = getString(
            if (isBossRush) R.string.boss_rush_top_10 else R.string.standard_top_10
        )

        // Save score, determine placement, populate leaderboard
        if (round > 0) {
            lifecycleScope.launch {
                try {
                    val ctx = requireContext().applicationContext
                    val (insertedId, topScores) = withContext(Dispatchers.IO) {
                        val db = ScoreDatabase.getInstance(ctx)
                        val id = db.scoreDao().insertScore(
                            ScoreEntity(
                                score = score,
                                round = round,
                                bricksDestroyed = bricksDestroyed,
                                boardClears = boardClears,
                                mulligansUsed = mulligansUsed,
                                shotsFired = shotsFired,
                                gameMode = gameModeName
                            )
                        )
                        val scores = if (isBossRush) {
                            db.scoreDao().getBossRushScores()
                        } else {
                            db.scoreDao().getStandardScores()
                        }
                        // Prune scores beyond top 10
                        if (isBossRush) {
                            db.scoreDao().pruneBossRushScores()
                        } else {
                            db.scoreDao().pruneStandardScores()
                        }
                        id to scores
                    }

                    if (_binding == null) return@launch

                    // Find placement
                    val rank = topScores.indexOfFirst { it.id == insertedId }
                    if (rank >= 0) {
                        val placement = rank + 1
                        if (placement == 1 && score > 0) {
                            binding.tvNewHighScore.visibility = View.VISIBLE
                        } else if (placement <= 10 && score > 0) {
                            binding.tvPlacement.text = getString(R.string.leaderboard_placement, placement)
                            binding.tvPlacement.visibility = View.VISIBLE
                        }
                    }

                    // Populate inline leaderboard
                    populateLeaderboard(topScores, insertedId)
                } catch (e: Exception) {
                    if (_binding != null) {
                        Toast.makeText(requireContext(), R.string.score_save_error, Toast.LENGTH_SHORT).show()
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

    private fun populateLeaderboard(scores: List<ScoreEntity>, highlightId: Long) {
        val b = _binding ?: return
        val container = b.leaderboardContainer
        container.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        for ((index, entry) in scores.withIndex()) {
            val row = inflater.inflate(R.layout.item_game_over_score, container, false)
            row.findViewById<TextView>(R.id.tvRank).text = "#${index + 1}"
            row.findViewById<TextView>(R.id.tvScore).text = "%,d pts".format(entry.score)
            row.findViewById<TextView>(R.id.tvDetails).text = "Rd ${entry.round}"

            if (entry.id == highlightId) {
                row.setBackgroundResource(R.drawable.bg_leaderboard_highlight)
            }
            container.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
