package com.scheduler.whatsapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.scheduler.whatsapp.model.ScheduledMessage
import com.scheduler.whatsapp.utils.Storage

class WhatsAppAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "WA_Accessibility"
        const val ACTION_SEND_MESSAGES = "com.scheduler.whatsapp.SEND_MESSAGES"
        const val EXTRA_MESSAGE_ID = "message_id"

        var instance: WhatsAppAccessibilityService? = null
        var isRunning = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingMessage: ScheduledMessage? = null
    private var pendingGroups: MutableList<String> = mutableListOf()
    private var currentGroupIndex = 0
    private var state = State.IDLE

    enum class State {
        IDLE,
        OPENING_WHATSAPP,
        SEARCHING_GROUP,
        TYPING_MESSAGE,
        SENDING,
        WAITING_NEXT_GROUP
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.d(TAG, "Accessibility Service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        isRunning = false
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || state == State.IDLE) return

        val packageName = event.packageName?.toString() ?: return

        when (state) {
            State.OPENING_WHATSAPP -> {
                if (packageName == "com.whatsapp") {
                    handler.postDelayed({ searchForGroup() }, 1500)
                    state = State.SEARCHING_GROUP
                }
            }
            State.SEARCHING_GROUP -> {
                if (packageName == "com.whatsapp") {
                    handler.postDelayed({ tryFindAndOpenGroup() }, 800)
                }
            }
            State.TYPING_MESSAGE -> {
                if (packageName == "com.whatsapp") {
                    handler.postDelayed({ typeAndSendMessage() }, 500)
                }
            }
            else -> {}
        }
    }

    fun startSendingMessage(messageId: String) {
        val message = Storage.loadMessages(this).find { it.id == messageId } ?: return
        val settings = Storage.loadSettings(this)

        pendingMessage = message
        pendingGroups = message.groups.toMutableList()
        currentGroupIndex = 0
        state = State.OPENING_WHATSAPP

        Log.d(TAG, "Starting to send: ${message.title} to ${pendingGroups.size} groups")

        // Open WhatsApp
        val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } else {
            Log.e(TAG, "WhatsApp not found!")
            state = State.IDLE
        }
    }

    private fun searchForGroup() {
        if (pendingGroups.isEmpty() || currentGroupIndex >= pendingGroups.size) {
            finishSending()
            return
        }

        val groupName = pendingGroups[currentGroupIndex]
        Log.d(TAG, "Looking for group: $groupName")

        // Find search button on WhatsApp main screen
        val root = rootInActiveWindow ?: run {
            handler.postDelayed({ searchForGroup() }, 1000)
            return
        }

        val searchButton = findNodeByDescription(root, "Pesquisar") 
            ?: findNodeByDescription(root, "Search")
            ?: findNodeById(root, "com.whatsapp:id/menuitem_search")

        if (searchButton != null) {
            searchButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            handler.postDelayed({ typeGroupName(groupName) }, 800)
        } else {
            handler.postDelayed({ searchForGroup() }, 1000)
        }
    }

    private fun typeGroupName(groupName: String) {
        val root = rootInActiveWindow ?: return
        val searchField = findNodeById(root, "com.whatsapp:id/search_input")
            ?: findNodeByClassName(root, "android.widget.EditText")

        if (searchField != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, groupName)
            searchField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            handler.postDelayed({ selectGroupFromSearch(groupName) }, 1500)
        } else {
            handler.postDelayed({ typeGroupName(groupName) }, 800)
        }
    }

    private fun tryFindAndOpenGroup() {
        if (state != State.SEARCHING_GROUP) return
        val groupName = pendingGroups.getOrNull(currentGroupIndex) ?: return
        typeGroupName(groupName)
    }

    private fun selectGroupFromSearch(groupName: String) {
        val root = rootInActiveWindow ?: return

        // Find the group in search results
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        collectAllNodes(root, allNodes)

        val groupNode = allNodes.find { node ->
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            (text.contains(groupName, ignoreCase = true) || desc.contains(groupName, ignoreCase = true))
                && node.isClickable
        }

        if (groupNode != null) {
            groupNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            state = State.TYPING_MESSAGE
            Log.d(TAG, "Opened group: $groupName")
        } else {
            Log.w(TAG, "Group not found in search: $groupName")
            // Try pressing back and skip this group
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({
                currentGroupIndex++
                state = State.SEARCHING_GROUP
                searchForGroup()
            }, 1000)
        }
    }

    private fun typeAndSendMessage() {
        val message = pendingMessage ?: return
        val settings = Storage.loadSettings(this)
        val root = rootInActiveWindow ?: return

        val inputField = findNodeById(root, "com.whatsapp:id/entry")
            ?: findNodeByClassName(root, "android.widget.EditText")

        if (inputField != null) {
            inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            handler.postDelayed({
                val args = Bundle()
                args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    message.message
                )
                inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

                // Wait typing delay then send
                handler.postDelayed({
                    sendCurrentMessage()
                }, settings.typingDelayMs)
            }, 500)
        } else {
            handler.postDelayed({ typeAndSendMessage() }, 800)
        }
    }

    private fun sendCurrentMessage() {
        val root = rootInActiveWindow ?: return
        val settings = Storage.loadSettings(this)

        val sendButton = findNodeById(root, "com.whatsapp:id/send")
            ?: findNodeByDescription(root, "Enviar")
            ?: findNodeByDescription(root, "Send")

        if (sendButton != null) {
            sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Message sent to group ${currentGroupIndex + 1}/${pendingGroups.size}")

            currentGroupIndex++
            state = State.WAITING_NEXT_GROUP

            val delay = settings.delayBetweenGroupsMs + (Math.random() * settings.randomDelayMaxMs).toLong()

            handler.postDelayed({
                if (currentGroupIndex < pendingGroups.size) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    handler.postDelayed({
                        state = State.SEARCHING_GROUP
                        searchForGroup()
                    }, 1500)
                } else {
                    finishSending()
                }
            }, delay)
        } else {
            handler.postDelayed({ sendCurrentMessage() }, 500)
        }
    }

    private fun finishSending() {
        Log.d(TAG, "Finished sending all messages")
        state = State.IDLE
        pendingMessage = null
        pendingGroups.clear()
        currentGroupIndex = 0

        // Go back to home screen
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Notify scheduler that sending is complete
        val intent = Intent(this, SchedulerService::class.java).apply {
            action = SchedulerService.ACTION_SENDING_COMPLETE
        }
        startService(intent)
    }

    override fun onInterrupt() {
        state = State.IDLE
        Log.d(TAG, "Service interrupted")
    }

    // --- Helper functions ---

    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        return nodes?.firstOrNull()
    }

    private fun findNodeByDescription(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (root.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true && root.isClickable) {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeByDescription(child, desc)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByClassName(root: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        if (root.className?.toString() == className) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeByClassName(child, className)
            if (found != null) return found
        }
        return null
    }

    private fun collectAllNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        list.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectAllNodes(child, list)
        }
    }
}
