package io.github.CMPUT301F26stderr.campsbridge.platform.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.CMPUT301F26stderr.campsbridge.R

/** Submits resolved standard notifications; it never queries events or changes app preferences.
 * @param context Host context retained only through its application context.
 */
class AndroidNotificationPublisher(
    context: Context,
) : NotificationPublisher {
    private val application = context.applicationContext

    /** Rechecks device allowance and submits a stable account/event notification or fails.
     * Android controls presentation; existing channel choices are never reset.
     * @param alert Already resolved event identity, typed target and text.
     * @throws IllegalStateException When current Android notification settings block submission.
     */
    @SuppressLint("MissingPermission")
    override fun post(alert: NotificationAlert) {
        check(AndroidNotificationPermission(application).status().canPost) {
            "Android notification settings block posting"
        }
        val entry = NotificationEntry(alert.accountId, alert.eventId, alert.subject)
        val intent = entry.intent(application)
        val pending =
            PendingIntent.getActivity(
                application,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = application.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(RESULT_NOTIFICATION_CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        RESULT_NOTIFICATION_CHANNEL,
                        application.getString(R.string.notification_results_channel),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description =
                            application.getString(R.string.notification_results_description)
                    },
                )
            }
        }
        val notification =
            NotificationCompat
                .Builder(application, RESULT_NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(alert.text.title)
                .setContentText(alert.text.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text.body))
                .setContentIntent(pending)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()
        NotificationManagerCompat
            .from(
                application,
            ).notify(entry.identityUri().toString(), ALERT_ID, notification)
    }
}

/** Fixed within each unique account/event tag; event hashes never supply notification IDs. */
private const val ALERT_ID = 1
