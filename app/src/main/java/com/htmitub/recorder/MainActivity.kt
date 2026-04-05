package com.htmitub.recorder

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.htmitub.recorder.databinding.ActivityMainBinding
import com.htmitub.recorder.db.Run
import com.htmitub.recorder.db.RunDatabase
import com.htmitub.recorder.sync.ApiClient
import com.htmitub.recorder.sync.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RunAdapter
    private lateinit var galleryLauncher: ActivityResultLauncher<String>
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private var cameraUri: Uri? = null
    private var selectedRun: Run? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            handlePhotoSelected(uri)
        }
        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) cameraUri?.let { handlePhotoSelected(it) }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = RunAdapter(
            onUploadClick = { SyncWorker.enqueue(this@MainActivity) },
            onRunClick = { run -> showPhotoDialog(run) },
        )

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
        SyncWorker.enqueue(this)
    }

    private fun loadRuns() {
        lifecycleScope.launch {
            val runs = RunDatabase.getInstance(this@MainActivity).runDao().getAllRuns()
            adapter.submitList(runs)
        }
    }

    private fun showPhotoDialog(run: Run) {
        selectedRun = run
        MaterialAlertDialogBuilder(this)
            .setTitle("Lägg till foto")
            .setItems(arrayOf("Välj från galleri", "Ta foto")) { _, which ->
                when (which) {
                    0 -> galleryLauncher.launch("image/*")
                    1 -> {
                        val tmpFile = File.createTempFile("photo_", ".jpg", cacheDir)
                        cameraUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", tmpFile)
                        cameraLauncher.launch(cameraUri!!)
                    }
                }
            }
            .show()
    }

    private fun handlePhotoSelected(uri: Uri) {
        val run = selectedRun ?: return
        lifecycleScope.launch {
            try {
                val imageBytes = withContext(Dispatchers.IO) { compressImage(uri) }
                val assetUrl = ApiClient().uploadPhoto(run.id, imageBytes)
                RunDatabase.getInstance(this@MainActivity).runDao().updatePhotoUrl(run.id, assetUrl)
                loadRuns()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Foto kunde inte laddas upp", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun compressImage(uri: Uri): ByteArray {
        val inputStream = contentResolver.openInputStream(uri)!!
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        val maxDim = 1200
        val scale = maxDim.toFloat() / maxOf(original.width, original.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true,
            )
        } else {
            original
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (bitmap !== original) bitmap.recycle()
        original.recycle()
        return out.toByteArray()
    }
}

class RunAdapter(
    private val onUploadClick: () -> Unit,
    private val onRunClick: (Run) -> Unit,
) : RecyclerView.Adapter<RunAdapter.ViewHolder>() {

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
        val ivPhoto: ImageView = view.findViewById(R.id.ivPhoto)

        init {
            btnUpload.setOnClickListener { onUploadClick() }
            itemView.setOnClickListener {
                val run = runs.getOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                if (run.syncStatus == "synced") onRunClick(run)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_run, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val run = runs[position]
        holder.tvDate.text = DATE_FMT.format(Date(run.startedAt))

        val distKm = "%.2f km".format(run.distanceM / 1000)
        val avgPace = if (run.avgSpeedMs > 0) {
            val secKm = (1000.0 / run.avgSpeedMs).toInt()
            "%d:%02d /km".format(secKm / 60, secKm % 60)
        } else "—"
        holder.tvStats.text = "$distKm · $avgPace"

        if (run.photoUrl != null) {
            holder.ivPhoto.visibility = View.VISIBLE
            holder.ivPhoto.load(run.photoUrl)
        } else {
            holder.ivPhoto.visibility = View.GONE
        }

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
            else -> {
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
