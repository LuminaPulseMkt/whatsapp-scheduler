package com.scheduler.whatsapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.scheduler.whatsapp.R
import com.scheduler.whatsapp.model.ScheduledMessage
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val messages: List<ScheduledMessage>,
    private val onEdit: (ScheduledMessage) -> Unit,
    private val onDelete: (ScheduledMessage) -> Unit,
    private val onToggle: (ScheduledMessage) -> Unit
) : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.msgTitle)
        val preview: TextView = view.findViewById(R.id.msgPreview)
        val groups: TextView = view.findViewById(R.id.msgGroups)
        val schedule: TextView = view.findViewById(R.id.msgSchedule)
        val nextSend: TextView = view.findViewById(R.id.msgNextSend)
        val toggle: Switch = view.findViewById(R.id.msgToggle)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        val sdf = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR"))

        holder.title.text = msg.title
        holder.preview.text = if (msg.message.length > 60) msg.message.take(60) + "..." else msg.message
        holder.groups.text = "📢 ${msg.groups.size} grupo(s): ${msg.groups.take(2).joinToString(", ")}${if (msg.groups.size > 2) "..." else ""}"
        holder.schedule.text = "🕐 A cada ${msg.intervalHours}h — início ${String.format("%02d:%02d", msg.startHour, msg.startMinute)}"
        
        holder.nextSend.text = if (msg.nextSendAt > 0) {
            "Próximo: ${sdf.format(Date(msg.nextSendAt))}"
        } else {
            "Não agendado"
        }

        holder.toggle.isChecked = msg.isActive
        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.setOnCheckedChangeListener { _, _ -> onToggle(msg) }

        holder.btnEdit.setOnClickListener { onEdit(msg) }
        holder.btnDelete.setOnClickListener { onDelete(msg) }
    }

    override fun getItemCount() = messages.size
}
