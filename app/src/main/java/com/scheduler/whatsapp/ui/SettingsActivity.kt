package com.scheduler.whatsapp.ui

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.scheduler.whatsapp.R
import com.scheduler.whatsapp.model.AppSettings
import com.scheduler.whatsapp.utils.Storage

class SettingsActivity : AppCompatActivity() {

    private lateinit var seekDelay: SeekBar
    private lateinit var seekTyping: SeekBar
    private lateinit var seekRandom: SeekBar
    private lateinit var tvDelay: TextView
    private lateinit var tvTyping: TextView
    private lateinit var tvRandom: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configurações"

        seekDelay = findViewById(R.id.seekDelay)
        seekTyping = findViewById(R.id.seekTyping)
        seekRandom = findViewById(R.id.seekRandom)
        tvDelay = findViewById(R.id.tvDelayValue)
        tvTyping = findViewById(R.id.tvTypingValue)
        tvRandom = findViewById(R.id.tvRandomValue)

        val settings = Storage.loadSettings(this)

        // Delay between groups (5-30 seconds)
        seekDelay.max = 25
        seekDelay.progress = ((settings.delayBetweenGroupsMs / 1000) - 5).toInt()
        updateDelayLabel(seekDelay.progress + 5)

        seekDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                updateDelayLabel(p + 5)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Typing delay (0-5 seconds)
        seekTyping.max = 50
        seekTyping.progress = (settings.typingDelayMs / 100).toInt()
        updateTypingLabel(seekTyping.progress * 100)

        seekTyping.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                updateTypingLabel(p * 100L)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Random delay (0-10 seconds)
        seekRandom.max = 100
        seekRandom.progress = (settings.randomDelayMaxMs / 100).toInt()
        updateRandomLabel(seekRandom.progress * 100L)

        seekRandom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                updateRandomLabel(p * 100L)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            val newSettings = AppSettings(
                delayBetweenGroupsMs = (seekDelay.progress + 5) * 1000L,
                typingDelayMs = seekTyping.progress * 100L,
                randomDelayMaxMs = seekRandom.progress * 100L
            )
            Storage.saveSettings(this, newSettings)
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun updateDelayLabel(seconds: Int) {
        tvDelay.text = "${seconds}s entre grupos"
    }

    private fun updateTypingLabel(ms: Long) {
        tvTyping.text = "${ms / 1000.0}s simulando digitação"
    }

    private fun updateRandomLabel(ms: Long) {
        tvRandom.text = "até ${ms / 1000.0}s de variação aleatória"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
