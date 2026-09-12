package com.example.androidkotlinapp

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri

/**
 * The one sound every alerting notification in this app uses.
 *
 * A channel's sound is fixed the moment the channel is created and cannot be changed
 * afterwards, so each channel that wants this carries a version in its id: bump the version
 * and the old channel is replaced rather than silently keeping the sound it was born with.
 */
object NotificationSound {

    fun uri(context: Context): Uri = Uri.parse(
        "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.focus_notification}"
    )

    /** Played as a notification, so it follows the notification volume and Do Not Disturb. */
    fun attributes(): AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .build()
}
