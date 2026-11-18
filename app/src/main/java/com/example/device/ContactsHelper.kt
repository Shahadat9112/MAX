package com.example.device

import android.content.Context
import android.provider.ContactsContract
import com.example.domain.ContactMatch

/**
 * Queries the device contacts provider locally with English and Bengali relationship-alias expansion.
 */
class ContactsHelper(private val context: Context) {

    fun findContacts(query: String): List<ContactMatch> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) return emptyList()

        // Expand common relationship aliases in English and Bengali
        val candidates = expandRelationshipAliases(cleanQuery)
        val results = mutableListOf<ContactMatch>()
        val seenNumbers = mutableSetOf<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use { c ->
            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (c.moveToNext()) {
                val displayName = c.getString(nameIdx)?.trim().orEmpty()
                val rawNumber = c.getString(numIdx)?.trim().orEmpty()
                if (displayName.isBlank() || rawNumber.isBlank()) continue

                val lowerName = displayName.lowercase()
                val matches = candidates.any { alias ->
                    lowerName == alias || lowerName.contains(alias) || alias.contains(lowerName)
                }
                if (matches) {
                    val normalizedNumber = rawNumber.replace(Regex("[\\s\\-()]"), "")
                    if (seenNumbers.add(normalizedNumber)) {
                        results.add(ContactMatch(name = displayName, phoneNumber = rawNumber))
                    }
                }
            }
        }

        // If user directly provided digits (e.g., "call 01712345678")
        if (results.isEmpty() && cleanQuery.count { it.isDigit() } >= 3) {
            results.add(ContactMatch(name = query.trim(), phoneNumber = query.trim()))
        }

        return results
    }

    private fun expandRelationshipAliases(query: String): List<String> {
        val stripped = query
            .removePrefix("my ")
            .removePrefix("আমার ")
            .trim()
        return when (stripped) {
            "father", "dad", "daddy", "papa", "abbu", "baba", "বাবা", "আব্বু" ->
                listOf("father", "dad", "daddy", "papa", "abbu", "baba", "বাবা", "আব্বু")
            "mother", "mom", "mommy", "mama", "ammu", "ma", "মা", "আম্মু" ->
                listOf("mother", "mom", "mommy", "mama", "ammu", "ma", "মা", "আম্মু")
            "brother", "bro", "bhai", "ভাই", "ভাইয়া" ->
                listOf("brother", "bro", "bhai", "ভাই", "ভাইয়া")
            "sister", "sis", "apu", "bon", "বোন", "আপু" ->
                listOf("sister", "sis", "apu", "bon", "বোন", "আপু")
            else -> listOf(stripped)
        }
    }
}
