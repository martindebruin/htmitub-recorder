package com.htmitub.recorder

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.htmitub.recorder.databinding.ActivityRecordingBinding
import com.htmitub.recorder.sync.SyncWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private var service: RecordingService? = null
    private var runStarted = false
    private var collectorJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as RecordingService.LocalBinder).getService()
            if (!runStarted) {
                service!!.startRun()
                runStarted = true
            }
            collectorJob?.cancel()
            collectorJob = lifecycleScope.launch {
                service!!.state.collect { state -> updateUI(state) }
            }
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ), RC_LOCATION)
        } else {
            bindAndStart()
        }

        binding.btnPause.setOnClickListener {
            val svc = service ?: return@setOnClickListener
            if (svc.state.value.isPaused) {
                svc.resumeRun()
                binding.btnPause.text = "PAUSE"
            } else {
                svc.pauseRun()
                binding.btnPause.text = "RESUME"
            }
        }

        binding.btnStop.setOnClickListener {
            service?.stopRun()
            SyncWorker.enqueue(applicationContext)
            finish()
        }
    }

    private fun bindAndStart() {
        val intent = Intent(this, RecordingService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun updateUI(state: RecordingState) {
        binding.tvDistance.text = "%.2f".format(state.distanceM / 1000)
        binding.tvCurrentPace.text = state.currentPaceSecKm?.let { formatPace(it) } ?: "—"
        binding.tvAvgPace.text = state.avgPaceSecKm?.let { formatPace(it) } ?: "—"
    }

    private fun formatPace(secKm: Int): String {
        val m = secKm / 60
        val s = secKm % 60
        return "%d:%02d".format(m, s)
    }

    private fun hasLocationPermission() =
        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_LOCATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            bindAndStart()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (service != null) unbindService(connection)
    }

    companion object { private const val RC_LOCATION = 1001 }
}
