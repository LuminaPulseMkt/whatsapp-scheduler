package com.scheduler.whatsapp.ui

import android.app.AlarmManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.scheduler.whatsapp.R
import com.scheduler.whatsapp.model.ScheduledMessage
import com.scheduler.whatsapp.service.SchedulerService
import com.scheduler.whatsapp.utils.Storage

class EditMessageActivity : AppCompatActivity() {

    private var editingMessage: ScheduledMessage? = null
    private val groupList = mutableListOf<String>()

    private lateinit var etTitle: TextInputEditText
    private lateinit var etMessage: TextInputEditText
    private lateinit var etGroupName: TextInputEditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var spinnerInterval: Spinner
    private lateinit var timePicker: TimePicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_message)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        etTitle = findViewById(R.id.etTitle)
        etMessage = findViewById(R.id.etMessage)
        etGroupName = findViewById(R.id.etGroupName)
        chipGroup = findViewById(R.id.chipGroup)
        spinnerInterval = findViewById(R.id.spinnerInterval)
        timePicker = findViewById(R.id.timePicker)
        timePicker.setIs24HourView(true)

        // Setup interval spinner
        val intervals = arrayOf("1 hora", "2 horas", "3 horas", "4 horas", "6 horas", "8 horas", "12 horas", "24 horas")
        val intervalValues = intArrayOf(1, 2, 3, 4, 6, 8, 12, 24)
        spinnerInterval.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, intervals)

        // Add group button
        findViewById<Button>(R.id.btnAddGroup).setOnClickListener {
            val name = etGroupName.text?.toString()?.trim() ?: ""
            if (name.isNotEmpty() && !groupList.contains(name)) {
                addGroupChip(name)
                etGroupName.text?.clear()
            } else if (name.isEmpty()) {
                etGroupName.error = "Digite o nome do grupo"
            }
        }

        // Save button
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveMessage(intervalValues)
        }

        // Load existing message if editing
        val messageId = intent.getStringExtra("message_id")
        if (messageId != null) {
            editingMessage = Storage.loadMessages(this).find { it.id == messageId }
            editingMessage?.let { populateForm(it, intervalValues) }
            supportActionBar?.title = "Editar mensagem"
        } else {
            supportActionBar?.title = "Nova mensagem"
        }
    }

    private fun populateForm(msg: ScheduledMessage, intervalValues: IntArray) {
        etTitle.setText(msg.title)
        etMessage.setText(msg.message)
        msg.groups.forEach { addGroupChip(it) }
        
        val intervalIndex = intervalValues.indexOf(msg.intervalHours)
        if (intervalIndex >= 0) spinnerInterval.setSelection(intervalIndex)
        
        timePicker.hour = msg.startHour
        timePicker.minute = msg.startMinute
    }

    private fun addGroupChip(name: String) {
        groupList.add(name)
        val chip = Chip(this).apply {
            text = name
            isCloseIconVisible = true
            setOnCloseIconClickListener {
                groupList.remove(name)
                chipGroup.removeView(this)
            }
        }
        chipGroup.addView(chip)
    }

    private fun saveMessage(intervalValues: IntArray) {
        val title = etTitle.text?.toString()?.trim() ?: ""
        val message = etMessage.text?.toString()?.trim() ?: ""
        val intervalHours = intervalValues[spinnerInterval.selectedItemPosition]

        if (title.isEmpty()) {
            etTitle.error = "Digite um título"
            return
        }
        if (message.isEmpty()) {
            etMessage.error = "Digite a mensagem"
            return
        }
        if (groupList.isEmpty()) {
            Toast.makeText(this, "Adicione pelo menos um grupo", Toast.LENGTH_SHORT).show()
            return
        }

        val newMessage = ScheduledMessage(
            id = editingMessage?.id ?: java.util.UUID.randomUUID().toString(),
            title = title,
            message = message,
            groups = groupList.toList(),
            intervalHours = intervalHours,
            startHour = timePicker.hour,
            startMinute = timePicker.minute,
            isActive = editingMessage?.isActive ?: true
        )

        Storage.updateMessage(this, newMessage)

        if (newMessage.isActive) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            SchedulerService.scheduleNext(this, am, newMessage)
        }

        Toast.makeText(this, "Mensagem salva!", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
