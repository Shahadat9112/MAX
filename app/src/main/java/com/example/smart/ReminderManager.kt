package com.example.smart

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.example.permissions.PrivacyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Privacy-first Reminder controller for MAX.
 *
 * Handles English & Bengali reminder commands such as:
 * `"MAX, আগামীকাল সকাল ৮টার জন্য রিমাইন্ডার দাও।"`
 *
 * Never schedules background alarms silently without user initiation; keeps confirmation
 * and user control via system Alarm/Clock UI or explicit in-app state.
 */
class ReminderManager(context: Context) {
    private val appContext = context.applicationContext
    private val idGen = AtomicLong(1L)

    private val _reminders = MutableStateFlow<List<SmartReminderItem>>(emptyList())
    val reminders: StateFlow<List<SmartReminderItem>> = _reminders.asStateFlow()

    /**
     * Parses time & day expressions from English or Bengali text and registers a reminder.
     */
    fun createReminderFromCommand(
        rawCommand: String,
        isUserInitiated: Boolean = true,
        openClockUi: Boolean = true
    ): Result<SmartReminderItem> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return Result.failure(SecurityException("Creating reminders requires explicit user command."))
        }

        val (hour, minute, dayOffset) = parseReminderSchedule(rawCommand)
        val formatted = String.format("%02d:%02d", hour, minute)
        val dayLabel = if (dayOffset > 0) "Tomorrow" else "Today"
        val item = SmartReminderItem(
            id = idGen.getAndIncrement(),
            title = rawCommand.trim(),
            hour = hour,
            minute = minute,
            dayOffset = dayOffset,
            formattedTime = "$dayLabel $formatted"
        )
        _reminders.value = _reminders.value + item

        if (openClockUi) {
            runCatching {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "MAX Reminder")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
            }
        }

        return Result.success(item)
    }

    internal fun parseReminderSchedule(raw: String): Triple<Int, Int, Int> {
        val normalizedDigits = convertBengaliDigitsToAscii(raw)
        val lower = normalizedDigits.lowercase()

        val dayOffset = if (lower.contains("আগামীকাল") || lower.contains("tomorrow") || lower.contains("কাল")) 1 else 0

        val timeMatch = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|টা|টার)?""", RegexOption.IGNORE_CASE).find(lower)
        var hour = timeMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 9
        val minute = timeMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        val suffix = timeMatch?.groupValues?.getOrNull(3)?.lowercase().orEmpty()

        val isEveningOrNight = lower.contains("বিকাল") || lower.contains("সন্ধ্যা") || lower.contains("রাত") || suffix == "pm"
        val isMorning = lower.contains("সকাল") || lower.contains("ভোর") || suffix == "am"

        if (isEveningOrNight && hour in 1..11) {
            hour += 12
        } else if (isMorning && hour == 12) {
            hour = 0
        }

        return Triple(hour.coerceIn(0, 23), minute.coerceIn(0, 59), dayOffset)
    }

    private fun convertBengaliDigitsToAscii(input: String): String {
        val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val idx = bnDigits.indexOf(ch)
            if (idx >= 0) sb.append(('0'.code + idx).toChar()) else sb.append(ch)
        }
        return sb.toString()
    }

    fun clearReminders() {
        _reminders.value = emptyList()
    }
}
