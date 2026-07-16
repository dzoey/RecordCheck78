package com.recordcheck78

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simple persistence for the donation list using SharedPreferences + JSON.
 * (Avoids Room's kapt/ksp complexity for a straightforward list.)
 */
class DonationRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<DonationListItem> {
        val json = prefs.getString(KEY_LIST, "[]") ?: "[]"
        val arr = JSONArray(json)
        val items = mutableListOf<DonationListItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            items.add(deserialize(obj))
        }
        return items.sortedByDescending { it.addedAt }
    }

    fun getNeedsDonation(): List<DonationListItem> =
        getAll().filter { it.status == DonationStatus.NEEDS_DONATION }

    fun getAlreadyExists(): List<DonationListItem> =
        getAll().filter { it.status == DonationStatus.ALREADY_EXISTS }

    fun add(record: Record, checkResult: ArchiveCheckResult?): Long {
        val items = getAll().toMutableList()
        val id = (items.maxOfOrNull { it.id } ?: 0) + 1
        val status = when {
            checkResult?.exists == true -> DonationStatus.ALREADY_EXISTS
            checkResult?.error != null -> DonationStatus.NEEDS_DONATION
            else -> DonationStatus.NEEDS_DONATION
        }
        items.add(DonationListItem(id = id, record = record, checkResult = checkResult, status = status))
        save(items)
        return id
    }

    fun updateStatus(id: Long, status: DonationStatus) {
        val items = getAll().toMutableList()
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(status = status)
            save(items)
        }
    }

    fun remove(id: Long) {
        val items = getAll().toMutableList()
        items.removeAll { it.id == id }
        save(items)
    }

    fun clearAll() {
        prefs.edit().putString(KEY_LIST, "[]").apply()
    }

    private fun save(items: List<DonationListItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(serialize(it)) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun serialize(item: DonationListItem): JSONObject {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("addedAt", item.addedAt)
        obj.put("status", item.status.name)
        obj.put("record", JSONObject().apply {
            put("catalogNumber", item.record.catalogNumber)
            put("artist", item.record.artist)
            put("title", item.record.title)
            put("labelName", item.record.labelName)
            put("labelStyle", item.record.labelStyle)
            put("rawOcrText", item.record.rawOcrText)
            put("photoPath", item.record.photoPath)
            put("timestamp", item.record.timestamp)
        })
        if (item.checkResult != null) {
            obj.put("checkResult", JSONObject().apply {
                put("exists", item.checkResult.exists)
                put("searchQueryUsed", item.checkResult.searchQueryUsed)
                put("error", item.checkResult.error ?: "")
                val itemsArr = JSONArray()
                item.checkResult.archiveItems.forEach { ai ->
                    itemsArr.put(JSONObject().apply {
                        put("identifier", ai.identifier)
                        put("title", ai.title)
                        put("creator", ai.creator)
                        put("date", ai.date)
                        put("detailUrl", ai.detailUrl)
                        put("downloadUrl", ai.downloadUrl)
                    })
                }
                put("archiveItems", itemsArr)
            })
        }
        return obj
    }

    private fun deserialize(obj: JSONObject): DonationListItem {
        val recordObj = obj.getJSONObject("record")
        val record = Record(
            catalogNumber = recordObj.optString("catalogNumber"),
            artist = recordObj.optString("artist"),
            title = recordObj.optString("title"),
            labelName = recordObj.optString("labelName"),
            labelStyle = recordObj.optString("labelStyle"),
            rawOcrText = recordObj.optString("rawOcrText"),
            photoPath = recordObj.optString("photoPath"),
            timestamp = recordObj.optLong("timestamp")
        )
        val checkResult: ArchiveCheckResult? = if (obj.has("checkResult")) {
            val cr = obj.getJSONObject("checkResult")
            val itemsArr = cr.optJSONArray("archiveItems") ?: JSONArray()
            val items = mutableListOf<ArchiveItem>()
            for (i in 0 until itemsArr.length()) {
                val ai = itemsArr.getJSONObject(i)
                items.add(ArchiveItem(
                    identifier = ai.optString("identifier"),
                    title = ai.optString("title"),
                    creator = ai.optString("creator"),
                    date = ai.optString("date"),
                    detailUrl = ai.optString("detailUrl"),
                    downloadUrl = ai.optString("downloadUrl")
                ))
            }
            ArchiveCheckResult(
                record = record,
                exists = cr.optBoolean("exists"),
                archiveItems = items,
                searchQueryUsed = cr.optString("searchQueryUsed"),
                error = cr.optString("error").ifBlank { null }
            )
        } else null

        return DonationListItem(
            id = obj.optLong("id"),
            record = record,
            checkResult = checkResult,
            addedAt = obj.optLong("addedAt"),
            status = DonationStatus.valueOf(obj.optString("status", "NEEDS_DONATION"))
        )
    }

    companion object {
        private const val PREFS_NAME = "recordcheck78_prefs"
        private const val KEY_LIST = "donation_list"
    }
}