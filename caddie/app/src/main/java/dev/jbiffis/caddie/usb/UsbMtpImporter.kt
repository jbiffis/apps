package dev.jbiffis.caddie.usb

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.mtp.MtpConstants
import android.mtp.MtpDevice
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

/**
 * Imports FIT files straight off a Garmin watch over USB/OTG using Android's MTP
 * stack — no file picker needed (the system picker can't reliably browse MTP
 * devices, which is why "navigate to the watch" fails).
 *
 * Plug the watch into the phone with a USB-C/OTG cable; the watch mounts as an
 * MTP device. We open it, walk its object tree, and read every *.fit file,
 * preferring the golf folders (Activity / Scorecards / GolfCourse).
 */
class UsbMtpImporter(private val context: Context) {

    private val usbManager: UsbManager
        get() = context.getSystemService(Context.USB_SERVICE) as UsbManager

    sealed class Result {
        object NoDevice : Result()
        object PermissionDenied : Result()
        object OpenFailed : Result()
        data class Ok(val filesRead: Int, val newRounds: Int, val report: List<String>) : Result()
        data class Error(val message: String) : Result()
    }

    /** True if a watch (any USB device) is currently plugged in. */
    fun deviceAttached(): Boolean = usbManager.deviceList.isNotEmpty()

    /**
     * Read every FIT file off the attached watch, importing each via [onFit]
     * (which returns true when it created a new round). Runs blocking MTP I/O, so
     * call from a background dispatcher.
     */
    suspend fun importWatchFitFiles(
        onFit: suspend (name: String, bytes: ByteArray) -> String,
    ): Result {
        val device = usbManager.deviceList.values.firstOrNull() ?: return Result.NoDevice
        val report = ArrayList<String>()
        report.add("USB device ${device.deviceName} (vendor 0x${device.vendorId.toString(16)})")
        if (!usbManager.hasPermission(device) && !requestPermission(device)) {
            return Result.PermissionDenied
        }
        val connection = usbManager.openDevice(device) ?: return Result.OpenFailed
        val mtp = MtpDevice(device)
        if (!mtp.open(connection)) {
            connection.close()
            return Result.OpenFailed
        }
        var read = 0
        var newRounds = 0
        try {
            val storageIds = mtp.storageIds ?: IntArray(0)
            report.add("MTP storages: ${storageIds.size}")
            for (sid in storageIds) {
                val fits = collectFitHandles(mtp, sid, report)
                for (item in fits) {
                    val size = item.size
                    val where = "${item.folder.ifEmpty { "?" }}/${item.name}"
                    if (size <= 0 || size > MAX_FILE_BYTES) { report.add("  $where — skipped (size $size)"); continue }
                    val bytes = runCatching { mtp.getObject(item.handle, size) }.getOrNull()
                    if (bytes == null) { report.add("  $where — read failed"); continue }
                    read++
                    val outcome = onFit(item.name, bytes)
                    if (outcome.startsWith("NEW")) newRounds++
                    report.add("  $where (${bytes.size}b) → $outcome")
                    if (read >= MAX_FILES) { report.add("  reached file cap ($MAX_FILES)"); break }
                }
            }
        } catch (e: Exception) {
            report.add("ERROR: ${e.message ?: e}")
            return Result.Error(e.message ?: e.toString())
        } finally {
            mtp.close()
            connection.close()
        }
        return Result.Ok(read, newRounds, report)
    }

    private class FitItem(val handle: Int, val name: String, val size: Int, val folder: String)

    /**
     * Breadth-first walk of an MTP storage collecting *.fit files. Seeds from the
     * "all objects" query (0xFFFFFFFF) and also descends folders, so it works
     * whether the device returns a flat list or a hierarchy. Golf folders sort
     * first so a file cap keeps rounds, not monitoring data.
     */
    private fun collectFitHandles(mtp: MtpDevice, storageId: Int, report: MutableList<String>): List<FitItem> {
        val seen = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        fun enqueueChildren(parent: Int) {
            (mtp.getObjectHandles(storageId, 0, parent) ?: IntArray(0)).forEach {
                if (seen.add(it)) queue.add(it)
            }
        }
        enqueueChildren(0xFFFFFFFF.toInt()) // all objects on the storage
        if (seen.isEmpty()) enqueueChildren(0) // fall back to the root directory
        val fits = ArrayList<FitItem>()
        val folderNames = HashMap<Int, String>()
        val allFolders = HashSet<String>()
        while (queue.isNotEmpty() && seen.size < MAX_OBJECTS) {
            val handle = queue.removeFirst()
            val info = mtp.getObjectInfo(handle) ?: continue
            val name = info.name ?: continue
            if (info.format == MtpConstants.FORMAT_ASSOCIATION) {
                folderNames[handle] = name
                allFolders.add(name)
                enqueueChildren(handle)
            } else if (name.lowercase().endsWith(".fit")) {
                val folder = folderNames[info.parent] ?: ""
                fits.add(FitItem(handle, name, info.compressedSize, folder))
            }
        }
        val byFolder = fits.groupingBy { it.folder.ifEmpty { "(root)" } }.eachCount()
        report.add("Storage $storageId: scanned ${seen.size} objects, ${fits.size} .fit files")
        report.add("  folders: ${allFolders.sorted().joinToString(", ")}")
        report.add("  .fit by folder: ${byFolder.entries.joinToString(", ") { "${it.key}=${it.value}" }}")
        // Golf folders first; within a group, newest-looking names last (stable sort keeps order).
        return fits.sortedByDescending { GOLF_FOLDERS.contains(it.folder) }
    }

    /** Register a receiver and block until the user answers the USB permission prompt. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private suspend fun requestPermission(device: UsbDevice): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action == ACTION_USB_PERMISSION) {
                    deferred.complete(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags,
        )
        usbManager.requestPermission(device, pi)
        return try {
            deferred.await()
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "dev.jbiffis.caddie.USB_PERMISSION"
        private const val MAX_OBJECTS = 8000
        private const val MAX_FILES = 600
        private const val MAX_FILE_BYTES = 64 * 1024 * 1024
        private val GOLF_FOLDERS = setOf("Activity", "Scorecards", "GolfCourse", "GolfCourses", "NewFiles")
    }
}
