package com.guardian.app.callprotect

import android.content.Context
import android.os.Build
import android.provider.BlockedNumberContract
import android.telecom.TelecomManager

object CallActionHelper {
    fun canEndCall(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    fun endCall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            return try {
                tm?.endCall() ?: false
            } catch (e: SecurityException) {
                false
            }
        }
        return false
    }

    fun blockNumber(context: Context, number: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val values = android.content.ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
                }
                context.contentResolver.insert(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    values
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
