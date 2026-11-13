package io.github.eltonmaster.fastshopper.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.eltonmaster.fastshopper.R
import io.github.eltonmaster.fastshopper.data.ChangeNotificationText
import io.github.eltonmaster.fastshopper.data.ItemFormat
import io.github.eltonmaster.fastshopper.data.ListChanges
import io.github.eltonmaster.fastshopper.data.MealieRepository
import io.github.eltonmaster.fastshopper.data.RemoteChanges
import io.github.eltonmaster.fastshopper.data.db.ItemEntity
import io.github.eltonmaster.fastshopper.ui.MainActivity

/**
 * Posts one notification per list when someone else added or changed items there, grouped
 * together. Tapping one opens that list. Mealie doesn't record who made a change, so the text
 * can't name a person.
 */
object ChangeNotifier {
    private const val CHANNEL_ID = "list_changes"
    private const val GROUP = "io.github.eltonmaster.fastshopper.LIST_CHANGES"
    private const val ITEM_ID = 1
    private const val SUMMARY_ID = 0

    /** The list on screen right now, if any: it gets no notification, you're looking at it. */
    @Volatile
    var visibleListId: String? = null
        private set

    /** Called when a list comes on screen: no notification for it, and its old one goes away. */
    fun onListShown(context: Context, listId: String) {
        visibleListId = listId
        cancel(context, listId)
    }

    fun onListHidden(listId: String) {
        if (visibleListId == listId) visibleListId = null
    }

    /** Whether the app may post notifications (permission on Android 13+, and not blocked). */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Posts a notification for every list with changes no notification mentioned yet, then
     * remembers them as notified. The list on screen is skipped (and counts as notified).
     */
    suspend fun notifyPending(context: Context, repository: MealieRepository) {
        if (!canPost(context)) return
        ensureChannel(context)
        val visible = visibleListId
        var posted = false
        repository.changesToNotify().forEach { changes ->
            if (changes.listId != visible) {
                if (!post(context, changes)) return
                posted = true
            }
            repository.markNotified(changes.items.map { it.id })
        }
        if (posted) postSummary(context)
    }

    private fun post(context: Context, changes: ListChanges): Boolean {
        val (newItems, changedItems) = changes.items.partition { it.remoteChange == RemoteChanges.NEW }
        val name = { item: ItemEntity -> ItemFormat.title(item.foodName, item.note, item.display) }
        val text = ChangeNotificationText.text(newItems.map(name), changedItems.map(name))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(changes.listName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openListIntent(context, changes.listId))
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        return notify(context, changes.listId, ITEM_ID, notification)
    }

    /** Keeps the per-list notifications together in one group. */
    private fun postSummary(context: Context) {
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Shopping lists changed")
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .build()
        notify(context, null, SUMMARY_ID, summary)
    }

    private fun notify(context: Context, tag: String?, id: Int, notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            NotificationManagerCompat.from(context).notify(tag, id, notification)
            true
        } catch (e: SecurityException) {
            false // permission revoked in the meantime
        }
    }

    /** Removes the list's notification, and the group summary once no list is left. */
    fun cancel(context: Context, listId: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(listId, ITEM_ID)
        val left = manager.activeNotifications.any {
            it.notification.group == GROUP && it.id == ITEM_ID && it.tag != listId
        }
        if (!left) manager.cancel(SUMMARY_ID)
    }

    /** Removes all change notifications, e.g. when they're turned off or on sign-out. */
    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.activeNotifications
            .filter { it.notification.group == GROUP }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "List changes", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When someone else adds or changes items on a list you've opened"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun openListIntent(context: Context, listId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_LIST)
            .putExtra(MainActivity.EXTRA_LIST_ID, listId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            listId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
