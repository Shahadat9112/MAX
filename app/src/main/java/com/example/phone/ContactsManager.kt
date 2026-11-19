package com.example.phone

import android.Manifest
import android.content.Context
import com.example.device.ContactsHelper
import com.example.domain.ContactMatch
import com.example.permissions.PermissionManager
import com.example.permissions.PrivacyController

/**
 * Privacy-first Contacts lookup controller for MAX.
 *
 * Rules:
 * - Never reads contacts in the background or secretly.
 * - Never edits, modifies, or deletes contacts automatically.
 * - Only queries contacts when explicitly triggered by a user call/SMS lookup request
 *   and `READ_CONTACTS` permission is granted.
 */
class ContactsManager(
    context: Context,
    private val permissionManager: PermissionManager
) {
    private val contactsHelper = ContactsHelper(context.applicationContext)

    fun hasContactsPermission(): Boolean = permissionManager.hasContactsPermission()

    fun requiredPermission(): String = Manifest.permission.READ_CONTACTS

    /**
     * Queries local device contacts strictly for an active user-initiated lookup.
     */
    fun findContacts(
        query: String,
        isUserInitiated: Boolean
    ): List<ContactMatch> {
        if (!PrivacyController.assertUserInitiatedAction(isUserInitiated) || !isUserInitiated) {
            return emptyList()
        }
        if (!PrivacyController.verifyPermissionBeforeAction("READ_CONTACTS", hasContactsPermission())) {
            return emptyList()
        }
        return contactsHelper.findContacts(query)
    }
}
