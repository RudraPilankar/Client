package com.client.helpers

import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import android.util.Log
import java.io.File

object Utils {
    fun isSymlink(file: File): Boolean {
        return try {
            val stat: StructStat = Os.lstat(file.absolutePath)
            (stat.st_mode and OsConstants.S_IFMT) == OsConstants.S_IFLNK
        } catch (e: Exception) {
            Log.e("SymlinkCheck", "Error checking symlink", e)
            false
        }
    }
}