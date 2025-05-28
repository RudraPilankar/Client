package com.client.helpers

import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile


/**
 * Unzips this ZIP [File] into [targetDir].
 *
 * @throws IOException if anything goes wrong.
 */
@Throws(IOException::class)
fun File.unzipTo(targetDir: File) {
    // ensure output directory exists
    if (!targetDir.exists() && !targetDir.mkdirs()) {
        throw IOException("Could not create target directory: ${targetDir.absolutePath}")
    }

    ZipFile(this).use { zip ->
        zip.entries().asSequence().forEach { entry ->
            val outFile = File(targetDir, entry.name)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                // make sure parent dirs exist
                outFile.parentFile?.takeIf { !it.exists() }?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

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

    /**
     * Returns the most preferred ABI that this device supports.
     * On Android Lollipop (API 21)+ it uses Build.SUPPORTED_ABIS;
     * on older devices it falls back to ro.product.cpu.abi and abi2.
     */
    fun getBestAbi(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // SUPPORTED_ABIS is sorted by preference, most preferred first
            val abis = Build.SUPPORTED_ABIS
            if (abis != null && abis.size > 0) {
                return abis[0]
            }
        } else {
            // pre-Lollipop
            val primary = Build.CPU_ABI // most preferred
            val secondary = Build.CPU_ABI2 // fallback (may be empty)
            if (primary != null && !primary.isEmpty()) {
                return primary
            }
            if (secondary != null && !secondary.isEmpty()) {
                return secondary
            }
        }
        // As a last resort, default to a common 32-bit ARM ABI
        return "armeabi-v7a"
    }

    /**
     * Copies a resource in res/raw (e.g. R.raw.mydata) out to the given file.
     *
     * @param ctx       any Context (Activity, Service, etc.)
     * @param resId     resource ID (e.g. R.raw.mydata)
     * @param outFile   destination File on storage (must be writable)
     * @throws IOException if reading or writing fails
     */
    @Throws(IOException::class)
    fun extractRawResource(ctx: Context, resId: Int, outFile: File) {
        ctx.getResources().openRawResource(resId).use { `in` ->
            FileOutputStream(outFile).use { out ->
                val buf = ByteArray(8 * 1024)
                var len: Int
                while ((`in`.read(buf).also { len = it }) > 0) {
                    out.write(buf, 0, len)
                }
                out.flush()
            }
        }
    }
}