package com.htmitub.recorder

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.htmitub.recorder.databinding.ActivityMainBinding
import com.htmitub.recorder.db.Run
import com.htmitub.recorder.db.RunDatabase
import com.htmitub.recorder.sync.SyncWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RunAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = RunAdapter(onUploadClick = {
            // SyncWorker already picks up failed runs — just trigger it
            SyncWorker.enqueue(this@MainActivity)
        })

        binding.rvRuns.layoutManager = LinearLayoutManager(this)
        binding.rvRuns.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.rvRuns.adapter = adapter

        binding.btnStartRun.setOnClickListener {
            startActivity(Intent(this, RecordingActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadRuns()
        SyncWorker.enqueue(this) // Auto-sync pending runs on app open
    }

    private fun loadRuns() {
        lifecycleScope.launch {
            val runs = RunDatabase.getInstance(this@MainActivity).runDao().getAllRuns()
            adapter.submitList(runs)
        }
    }
}

class RunAdapter(private val onUploadClick: () -> Unit) :
    RecyclerView.Adapter<RunAdapter.ViewHolder>() {

    private var runs: List<Run> = emptyList()

    fun submitList(list: List<Run>) {
        runs = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvStats: TextView = view.findViewById(R.id.tvStats)
        val tvSyncStatus: TextView = view.findViewById(R.id.tvSyncStatus)
        val btnUpload: MaterialButton = view.findViewById(R.id.btnUpload)

        init {
            btnUpload.setOnClickListener { onUploadClick() }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = layoutInflater(parent).inflate(R.layout.item_run, parent, false)
        return ViewHolder(view)
    }

    private fun layoutInflater(parent: ViewGroup) =
        android.view.LayoutInflater.from(parent.context)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val run = runs[position]
        holder.tvDate.text = DATE_FMT.format(Date(run.startedAt))

        val distKm = "%.2f km".format(run.distanceM / 1000)
        val avgPace = if (run.avgSpeedMs > 0) {
            val secKm = (1000.0 / run.avgSpeedMs).toInt()
            "%d:%02d /km".format(secKm / 60, secKm % 60)
        } else "—"
        holder.tvStats.text = "$distKm · $avgPace"

        when (run.syncStatus) {
            "synced" -> {
                holder.tvSyncStatus.text = "✓"
                holder.tvSyncStatus.setTextColor(0xFF22C55E.toInt())
                holder.btnUpload.visibility = View.GONE
            }
            "failed" -> {
                holder.tvSyncStatus.text = "!"
                holder.tvSyncStatus.setTextColor(0xFFC0392B.toInt())
                holder.btnUpload.visibility = View.VISIBLE
            }
            else -> { // pending
                holder.tvSyncStatus.text = "…"
                holder.tvSyncStatus.setTextColor(0xFF888888.toInt())
                holder.btnUpload.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = runs.size

    companion object {
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
}
