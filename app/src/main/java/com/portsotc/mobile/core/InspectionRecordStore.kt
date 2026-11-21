package com.portsotc.mobile.core

import android.content.Context
import android.content.SharedPreferences
import com.portsotc.mobile.disc.DiscInspector

/** The smallest durable proof that a BOOT2 identifier was computed locally. */
data class InspectionRecord(
    val serial: String?,
    val bootName: String?,
    val bootMd5: String,
    val imageSha256: String,
    val inspectedAt: Long
)

class InspectionRecordStore(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("sotc_inspection", Context.MODE_PRIVATE)

    fun save(report: DiscInspector.Report, now: Long = System.currentTimeMillis()): InspectionRecord? {
        val bootMd5 = report.bootMd5 ?: return null
        val record = InspectionRecord(
            serial = report.serial,
            bootName = report.bootName,
            bootMd5 = bootMd5,
            imageSha256 = report.imageSha256,
            inspectedAt = now
        )
        preferences.edit()
            .putString(KEY_SERIAL, record.serial ?: "")
            .putString(KEY_BOOT_NAME, record.bootName ?: "")
            .putString(KEY_BOOT_MD5, record.bootMd5)
            .putString(KEY_IMAGE_SHA256, record.imageSha256)
            .putLong(KEY_INSPECTED_AT, record.inspectedAt)
            .apply()
        return record
    }

    fun latest(): InspectionRecord? {
        val bootMd5 = preferences.getString(KEY_BOOT_MD5, null) ?: return null
        if (bootMd5.length != 32) return null
        return InspectionRecord(
            serial = preferences.getString(KEY_SERIAL, null)?.takeIf { it.isNotEmpty() },
            bootName = preferences.getString(KEY_BOOT_NAME, null)?.takeIf { it.isNotEmpty() },
            bootMd5 = bootMd5,
            imageSha256 = preferences.getString(KEY_IMAGE_SHA256, null) ?: "",
            inspectedAt = preferences.getLong(KEY_INSPECTED_AT, 0L)
        )
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_SERIAL = "serial"
        const val KEY_BOOT_NAME = "boot_name"
        const val KEY_BOOT_MD5 = "boot_md5"
        const val KEY_IMAGE_SHA256 = "image_sha256"
        const val KEY_INSPECTED_AT = "inspected_at"
    }
}
