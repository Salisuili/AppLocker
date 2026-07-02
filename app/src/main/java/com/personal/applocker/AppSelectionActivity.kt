package com.personal.applocker

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var prefs: SharedPreferences
    private val appList = mutableListOf<AppInfo>()
    private lateinit var adapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_selection_layout)

        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        recyclerView = findViewById(R.id.app_list_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        val packageManager = packageManager
        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val lockedApps = prefs.getStringSet("locked_apps", emptySet()) ?: emptySet()

        appList.clear()
        for (app in packages) {
            if (packageManager.getLaunchIntentForPackage(app.packageName) != null) {
                appList.add(
                    AppInfo(
                        app.loadLabel(packageManager).toString(),
                        app.packageName,
                        lockedApps.contains(app.packageName)
                    )
                )
            }
        }

        appList.sortBy { it.name }
        adapter = AppAdapter(appList) { appInfo ->
            toggleAppLock(appInfo)
        }
        recyclerView.adapter = adapter
    }

    private fun toggleAppLock(appInfo: AppInfo) {
        val lockedApps = prefs.getStringSet("locked_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        
        if (appInfo.isLocked) {
            lockedApps.remove(appInfo.packageName)
        } else {
            lockedApps.add(appInfo.packageName)
        }
        
        prefs.edit().putStringSet("locked_apps", lockedApps).apply()
        appInfo.isLocked = !appInfo.isLocked
        adapter.notifyDataSetChanged()
        
        Toast.makeText(
            this,
            "${appInfo.name} ${if (appInfo.isLocked) "locked" else "unlocked"}",
            Toast.LENGTH_SHORT
        ).show()
    }
}

data class AppInfo(
    val name: String,
    val packageName: String,
    var isLocked: Boolean
)

class AppAdapter(
    private val apps: List<AppInfo>,
    private val onToggle: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.app_name)
        val appPackage: TextView = view.findViewById(R.id.app_package)
        val lockSwitch: Switch = view.findViewById(R.id.lock_switch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.app_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.appName.text = app.name
        holder.appPackage.text = app.packageName
        holder.lockSwitch.isChecked = app.isLocked
        holder.lockSwitch.setOnCheckedChangeListener(null)
        holder.lockSwitch.setOnCheckedChangeListener { _, _ ->
            onToggle(app)
        }
    }

    override fun getItemCount() = apps.size
}