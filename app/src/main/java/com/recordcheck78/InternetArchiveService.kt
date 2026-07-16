package com.recordcheck78

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service for querying the Internet Archive's Advanced Search API.
 *
 * API docs: https://archive.org/advancedsearch.php
 *
 * Strategy:
 * 1. Search by catalog number (most precise)
 * 2. If no results, search by artist + title
 * 3. If still no results, search by title alone
 */
class InternetArchiveService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://archive.org/advancedsearch.php"
        private const val COLLECTION_78 = "georgeblood"  // 78rpm collection
        // Also check the general 78rpm collection
        private const val COLLECTION_78_ALT = "78rpm"
    }

    /**
     * Check if a record exists on the Internet Archive.
     * Tries multiple search strategies in order of precision.
     */
    suspend fun checkRecord(record: Record): ArchiveCheckResult = withContext(Dispatchers.IO) {
        // Strategy 1: Search by catalog number
        if (record.catalogNumber.isNotBlank()) {
            val query = buildCatalogQuery(record.catalogNumber)
            val result = searchArchive(query, record)
            if (result.exists) return@withContext result
        }

        // Strategy 2: Search by artist + title
        if (record.artist.isNotBlank() && record.title.isNotBlank()) {
            val query = buildArtistTitleQuery(record.artist, record.title)
            val result = searchArchive(query, record)
            if (result.exists) return@withContext result
        }

        // Strategy 3: Search by title alone
        if (record.title.isNotBlank()) {
            val query = buildTitleQuery(record.title)
            val result = searchArchive(query, record)
            if (result.exists) return@withContext result
        }

        // Strategy 4: Broad search with whatever we have
        val broadQuery = buildBroadQuery(record)
        if (broadQuery.isNotBlank()) {
            return@withContext searchArchive(broadQuery, record)
        }

        ArchiveCheckResult(record = record, exists = false, error = "No searchable text found")
    }

    private fun buildCatalogQuery(catalogNumber: String): String {
        val cleanNum = catalogNumber.replace(Regex("[^a-zA-Z0-9\\-]"), "")
        return "collection:($COLLECTION_78 OR $COLLECTION_78_ALT) AND identifier:$cleanNum"
    }

    private fun buildArtistTitleQuery(artist: String, title: String): String {
        return "collection:($COLLECTION_78 OR $COLLECTION_78_ALT) AND creator:\"${escape(artist)}\" AND title:\"${escape(title)}\""
    }

    private fun buildTitleQuery(title: String): String {
        return "collection:($COLLECTION_78 OR $COLLECTION_78_ALT) AND title:\"${escape(title)}\""
    }

    private fun buildBroadQuery(record: Record): String {
        val parts = mutableListOf<String>()
        if (record.catalogNumber.isNotBlank()) parts.add(record.catalogNumber)
        if (record.artist.isNotBlank()) parts.add("\"${escape(record.artist)}\"")
        if (record.title.isNotBlank()) parts.add("\"${escape(record.title)}\"")
        if (parts.isEmpty()) return ""
        return "collection:($COLLECTION_78 OR $COLLECTION_78_ALT) AND (${parts.joinToString(" AND ")})"
    }

    private fun escape(s: String): String {
        return s.replace("\"", "\\\"")
    }

    private suspend fun searchArchive(query: String, record: Record): ArchiveCheckResult {
        val url = "$BASE_URL?q=${java.net.URLEncoder.encode(query, "UTF-8")}" +
                "&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=date" +
                "&fl[]=downloads&rows=10&output=json"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RecordCheck78/1.0 (78rpm donation checker)")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) {
                ArchiveCheckResult(record = record, exists = false, error = "HTTP ${response.code}")
            } else {
                parseSearchResults(body, query, record)
            }
        } catch (e: Exception) {
            ArchiveCheckResult(record = record, exists = false, error = e.message)
        }
    }

    private fun parseSearchResults(json: String, query: String, record: Record): ArchiveCheckResult {
        val root = JSONObject(json)
        val response = root.optJSONObject("response") ?: return ArchiveCheckResult(record, false, searchQueryUsed = query)
        val docs = response.optJSONArray("docs") ?: return ArchiveCheckResult(record, false, searchQueryUsed = query)
        val numFound = response.optInt("numFound", 0)

        if (numFound == 0 || docs.length() == 0) {
            return ArchiveCheckResult(record = record, exists = false, searchQueryUsed = query)
        }

        val items = mutableListOf<ArchiveItem>()
        for (i in 0 until docs.length()) {
            val doc = docs.getJSONObject(i)
            val identifier = doc.optString("identifier", "")
            items.add(ArchiveItem(
                identifier = identifier,
                title = doc.optString("title", ""),
                creator = doc.optString("creator", ""),
                date = doc.optString("date", ""),
                detailUrl = "https://archive.org/details/$identifier",
                downloadUrl = "https://archive.org/download/$identifier"
            ))
        }

        return ArchiveCheckResult(
            record = record,
            exists = true,
            archiveItems = items,
            searchQueryUsed = query
        )
    }
}