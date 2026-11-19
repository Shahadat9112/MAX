package com.example.smart

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.example.permissions.PrivacyController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Privacy-first Calendar controller for MAX.
 *
 * Handles requests such as `"MAX, আজকের ক্যালেন্ডার দেখাও।"`
 * Rules:
 * - Never reads personal calendar events in background without user request/permission.
 * - Opens Android's native Calendar view cleanly to today's date upon explicit command.
 */
class CalendarManager(context: Context) {
    private val appContext = context.applicationContext

    fun openTodaysCalendar(isUserInitiated: Boolean = true): Result<String> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("Opening calendar requires explicit user command."))
        }
        val nowMillis = System.currentTimeMillis()
        val builder = CalendarContract.CONTENT_URI.buildUpon()
        builder.appendPath("time")
        ContentUris.appendId(builder, nowMillis)

        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = builder.build()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
            SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(nowMillis))
        }
    }

    fun getTodaySummaryMessage(isBengali: Boolean): String {
        val now = Date()
        return if (isBengali) {
            val dateStr = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.forLanguageTag("bn-BD")).format(now)
            "আজ $dateStr। আপনার ফোনের ক্যালেন্ডার খোলা হচ্ছে।"
        } else {
            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(now)
            "Today is $dateStr. Opening your calendar."
        }
    }
}
