package com.scheduler.whatsapp.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.scheduler.whatsapp.R
import com.scheduler.whatsapp.model.ScheduledMessage
import com.scheduler.whatsapp.service.SchedulerService
import com.scheduler.whatsapp.service.WhatsAppAccessibilityService
import com.scheduler.whatsapp.utils.Storage
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<ScheduledMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)

        adapter = MessageAdapter(messages,
            onEdit = { msg -> openEditScreen(msg) },
            onDelete = { msg -> confirmDelete(msg) },
            onToggle = { msg -> toggleMessage(msg) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            openEditScreen(null)
        }

        checkAccessibilityService()
        requestBatteryOptimization()
    }

    override fun onResume() {
        super.onResume()
        loadMessages()
        updateAccessibilityStatus()
    }

    private fun loadMessages() {
        messages.clear()
        messages.addAll(Storage.loadMessages(this))
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openEditScreen(message: ScheduledMessage?) {
        val intent = Intent(this, EditMessageActivity::class.java)
        if (message != null) {
            intent.putExtra("message_id", message.id)
        }
        startActivity(intent)
    }

    private fun confirmDelete(message: ScheduledMessage) {
        AlertDialog.Builder(this)
            .setTitle("Excluir mensagem")
            .setMessage("Deseja excluir \"${message.title}\"?")
            .setPositiveButton("Excluir") { _, _ ->
                Storage.deleteMessage(this, message.id)
                SchedulerService.cancelSchedule(this, message)
                loadMessages()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toggleMessage(message: ScheduledMessage) {
        val updated = message.copy(isActive = !message.isActive)
        Storage.updateMessage(this, updated)

        if (updated.isActive) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            SchedulerService.scheduleNext(this, am, updated)
            Snackbar.make(recyclerView, "\"${message.title}\" ativado", Snackbar.LENGTH_SHORT).show()
        } else {
            SchedulerService.cancelSchedule(this, message)
            Snackbar.make(recyclerView, "\"${message.title}\" pausado", Snackbar.LENGTH_SHORT).show()
        }
        loadMessages()
    }

    private fun checkAccessibilityService() {
        if (!isAccessibilityEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Permissão necessária")
                .setMessage(
                    "Para funcionar, o app precisa da permissão de Acessibilidade.\n\n" +
                    "Na próxima tela:\n" +
                    "1. Encontre \"Agendador WhatsApp\"\n" +
                    "2. Ative o serviço\n" +
                    "3. Confirme a permissão"
                )
                .setPositiveButton("Abrir configurações") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${WhatsAppAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabled?.contains(service) == true
    }

    private fun updateAccessibilityStatus() {
        val statusView = findViewById<TextView?>(R.id.accessibilityStatus) ?: return
        if (isAccessibilityEnabled()) {
            statusView.text = "✅ Acessibilidade ativa"
            statusView.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            statusView.text = "⚠️ Acessibilidade desativada - toque para ativar"
            statusView.setTextColor(getColor(android.R.color.holo_red_dark))
            statusView.setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) { /* ignore */ }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
