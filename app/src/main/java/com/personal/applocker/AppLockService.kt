package com.personal.applocker

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AppLockService : Service() {

    private lateinit var prefs: SharedPreferences
    private var isMonitoring = false
    private val checkInterval = 1000L // Check every 1 second

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMonitoring) {
            isMonitoring = true
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        Thread {
            while (isMonitoring) {
                try {
                    val lockedApps = prefs.getStringSet("locked_apps", emptySet()) ?: emptySet()
                    if (lockedApps.isNotEmpty()) {
                        val foregroundApp = getForegroundApp()
                        if (foregroundApp in lockedApps) {
                            showLockScreen(foregroundApp)
                        }
                    }
                    Thread.sleep(checkInterval)
                } catch (e: Exception) {
                    // Error monitoring
                }
            }
        }.start()
    }

    private fun getForegroundApp(): String {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 5000,
            currentTime
        )
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName ?: ""
    }

    private fun showLockScreen(packageName: String) {
        val intent = Intent(this, LockScreenActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("locked_package", packageName)
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "app_lock_service",
                "App Lock Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "app_lock_service")
            .setContentTitle("App Lock Active")
            .setContentText("Protecting your apps")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isMonitoring = false
        super.onDestroy()
    }
}