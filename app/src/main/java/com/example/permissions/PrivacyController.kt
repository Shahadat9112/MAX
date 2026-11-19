package com.example.permissions

/**
 * Enforces strict MAX privacy rules and security checks before any sensitive action.
 *
 * MAX guarantees:
 * - Never opens camera automatically.
 * - Never accesses gallery automatically.
 * - Never reads files automatically.
 * - Never uses GPS/location automatically.
 * - Never browses the internet automatically.
 * - Never records private conversations in standby or saves audio.
 */
object PrivacyController {

    private var autoActionsBlocked = true

    fun verifyPermissionBeforeAction(actionName: String, hasPermission: Boolean): Boolean {
        if (!hasPermission) {
            return false
        }
        return true
    }

    /**
     * Ensures sensitive actions (camera, gallery, files, location) are strictly gated
     * behind direct user intent and explicit consent.
     */
    fun assertUserInitiatedAction(isUserInitiated: Boolean): Boolean {
        if (!isUserInitiated && autoActionsBlocked) {
            return false
        }
        return true
    }

    fun wipeBuffer(buffer: ByteArray?) {
        buffer?.fill(0)
    }

    fun wipeBuffer(buffer: CharArray?) {
        buffer?.fill('0')
    }
}
