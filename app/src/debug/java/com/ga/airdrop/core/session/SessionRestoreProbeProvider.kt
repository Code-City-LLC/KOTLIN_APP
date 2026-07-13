package com.ga.airdrop.core.session

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.push.PushDeepLink

/** Debug-only cross-process probe used by connected cold-restore tests. */
class SessionRestoreProbeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        require(method == METHOD_RESTORE)
        val appContext = requireNotNull(context).applicationContext
        AuthTokenStore.init(appContext)
        PushDeepLink.init(appContext)
        val snapshot = AuthTokenStore.snapshot()
        val result = Bundle().apply {
            putString(KEY_SESSION_ID, snapshot.sessionId)
            putBoolean(KEY_TOKEN_PRESENT, snapshot.token != null)
            putString(KEY_ROUTE, PushDeepLink.consume(snapshot))
            putInt(KEY_PROCESS_ID, Process.myPid())
        }
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 100L)
        return result
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val METHOD_RESTORE = "restore"
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_TOKEN_PRESENT = "tokenPresent"
        const val KEY_ROUTE = "route"
        const val KEY_PROCESS_ID = "processId"
    }
}
