package com.rapidfire.game.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.rapidfire.game.R
import com.rapidfire.game.data.ScoreDatabase
import com.rapidfire.game.data.ScoreEntity
import com.rapidfire.game.databinding.FragmentLeaderboardBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Set empty adapter immediately to avoid "No adapter attached; skipping layout"
        binding.rvScores.adapter = ScoreAdapter(emptyList())

        lifecycleScope.launch {
            val db = ScoreDatabase.getInstance(requireContext())
            val scores = db.scoreDao().getTopScores()

            if (scores.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvScores.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.GONE
                binding.rvScores.visibility = View.VISIBLE
                binding.rvScores.adapter = ScoreAdapter(scores)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class ScoreAdapter(private val scores: List<ScoreEntity>) :
        RecyclerView.Adapter<ScoreAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvRank: TextView = view.findViewById(R.id.tvRank)
            val tvScore: TextView = view.findViewById(R.id.tvScore)
            val tvDetails: TextView = view.findViewById(R.id.tvDetails)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_leaderboard, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = scores[position]
            val dateStr = SimpleDateFormat("MMM dd", Locale.getDefault())
                .format(Date(entry.timestamp))

            holder.tvRank.text = "#${position + 1}"
            holder.tvScore.text = "%,d pts".format(entry.score)
            holder.tvDetails.text = "Round ${entry.round} · ${entry.bricksDestroyed} bricks · ${entry.boardClears} clears"
            holder.tvDate.text = dateStr
        }

        override fun getItemCount() = scores.size
    }
}
