package com.munawar.ai

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Keeps recent notifications in memory only (never saved). Works only after the user grants Notification access.
 * Also lets Munawar reply to messages through the notification's own reply box and clear notifications on command.
 */
class NotifListener : NotificationListenerService() {
    override fun onListenerConnected() {
        inst = this
        try { activeNotifications?.forEach { add(it, false) } } catch (e: Exception) {}
    }

    override fun onListenerDisconnected() { if (inst === this) inst = null }

    override fun onNotificationPosted(sbn: StatusBarNotification) { add(sbn, true) }

    override fun onDestroy() {
        synchronized(items) { items.clear(); replyables.clear() }
        if (inst === this) inst = null
        super.onDestroy()
    }

    private fun add(sbn: StatusBarNotification, fresh: Boolean) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0 || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val app = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
        synchronized(items) {
            items.add(0, "$app: $title — $text".trim())
            while (items.size > 30) items.removeAt(items.size - 1)
            val act = n.actions?.firstOrNull { a -> a.remoteInputs != null && a.remoteInputs.isNotEmpty() }
            if (act != null) {
                replyables.removeAll { it.pkg == sbn.packageName }
                replyables.add(0, Replyable(sbn.packageName, app, title, act.actionIntent, act.remoteInputs))
                while (replyables.size > 8) replyables.removeAt(replyables.size - 1)
            }
        }
        if (fresh) Triggers.fire("notification", "$app $title $text")
    }

    class Replyable(val pkg: String, val app: String, val title: String, val intent: PendingIntent, val inputs: Array<RemoteInput>)

    companion object {
        private val items = ArrayList<String>()
        private val replyables = ArrayList<Replyable>()
        @Volatile var inst: NotifListener? = null

        fun recent(n: Int = 10): List<String> = synchronized(items) { items.take(n) }

        fun clearAll(): Boolean {
            val i = inst ?: return false
            return try { i.cancelAllNotifications(); true } catch (e: Exception) { false }
        }

        /** Sends [text] through the reply box of the latest message notification (optionally of an app whose name contains [appHint]). */
        fun reply(ctx: android.content.Context, appHint: String?, text: String): String? {
            val r = synchronized(items) {
                replyables.firstOrNull { appHint.isNullOrBlank() || it.app.contains(appHint, true) || it.pkg.contains(appHint, true) }
            } ?: return null
            return try {
                val intent = Intent()
                val bundle = Bundle()
                for (ri in r.inputs) bundle.putCharSequence(ri.resultKey, text)
                RemoteInput.addResultsToIntent(r.inputs, intent, bundle)
                r.intent.send(ctx, 0, intent)
                "Replied in ${r.app} to ${r.title.ifBlank { "the last message" }}: \"$text\""
            } catch (e: Exception) { null }
        }
    }
}
