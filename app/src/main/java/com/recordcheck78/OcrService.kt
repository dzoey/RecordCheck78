package com.recordcheck78

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * Service for extracting text (OCR) and label information from a photo of a 78rpm record.
 *
 * Uses ML Kit Text Recognition (Latin) for OCR and ML Kit Image Labeling
 * for identifying the label style/era.
 */
class OcrService {

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val imageLabeler: ImageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.DEFAULT_OPTIONS
    )

    /**
     * Extract record information from a photo of the record label.
     * Returns a Record with whatever fields could be identified.
     */
    suspend fun identifyRecord(photoPath: String): Record = withIO {
        val bitmap = loadBitmap(photoPath) ?: return@withIO Record(photoPath = photoPath)
        val image = InputImage.fromBitmap(bitmap, 0)

        // Run OCR and image labeling in parallel
        val ocrResult = runOcr(image)
        val labelResult = runImageLabeling(image)

        // Parse OCR text into structured fields
        val parsed = parseOcrText(ocrResult)

        Record(
            catalogNumber = parsed.catalogNumber,
            artist = parsed.artist,
            title = parsed.title,
            labelName = parsed.labelName,
            labelStyle = labelResult,
            rawOcrText = ocrResult,
            photoPath = photoPath
        )
    }

    private suspend fun runOcr(image: InputImage): String {
        return try {
            val result = textRecognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            ""
        }
    }

    private suspend fun runImageLabeling(image: InputImage): String {
        return try {
            val result = imageLabeler.process(image).await()
            // Filter for relevant labels (art, vintage, record, etc.)
            val relevantLabels = result.labels
                .filter { it.confidence > 0.5f }
                .joinToString(", ") { "${it.text} (${(it.confidence * 100).toInt()}%)" }
            relevantLabels
        } catch (e: Exception) {
            Log.e(TAG, "Image labeling failed", e)
            ""
        }
    }

    /**
     * Parse raw OCR text from a 78rpm label into structured fields.
     *
     * 78rpm labels typically have:
     * - Label name at top (Victor, Columbia, Decca, Brunswick, etc.)
     * - Catalog number (usually prominent, e.g., "20-1234", "V-12345")
     * - Artist name
     * - Song title
     *
     * We use heuristic patterns since label layouts vary widely.
     */
    private fun parseOcrText(text: String): ParsedRecord {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val fullText = text.trim()

        // Known 78rpm record labels
        val knownLabels = listOf(
            "Victor", "Victrola", "Columbia", "Decca", "Brunswick", "Vocalion",
            "Okeh", "OKeh", "Paramount", "Gennett", "Bluebird", "RCA Victor",
            "Capitol", "Mercury", "Audio Fidelity", "Hi-Fi", "London", "MGM",
            "Aristocrat", "Chess", "King", "Savoy", "Atlantic", "Imperial",
            "Specialty", "Modern", "Aladdin", "EmArcy", "Dot", "Cadence",
            "RCA", "HMV", "His Master's Voice", "Victor Talking Machine"
        )

        // Find label name
        var labelName = ""
        for (line in lines) {
            for (known in knownLabels) {
                if (line.contains(known, ignoreCase = true)) {
                    labelName = known
                    break
                }
            }
            if (labelName.isNotEmpty()) break
        }

        // Find catalog number — patterns like "20-1234", "V-12345", "B-12345",
        // "CO-12345", "40-0000", numeric-only, or alphanumeric with hyphens
        val catalogPattern = Regex(
            "(?:[A-Z]{1,3}[-\\s])?\\d{2,6}[-–]?\\d{0,4}|" +  // Standard patterns
            "[A-Z]{1,3}[-\\s]\\d{3,6}|" +                      // Letter prefix patterns
            "\\d{2,6}-\\d{2,4}"                                 // Numeric with hyphen
        )
        var catalogNumber = ""
        for (line in lines) {
            val match = catalogPattern.find(line)
            if (match != null && match.value.length >= 3) {
                catalogNumber = match.value.trim()
                break
            }
        }
        // Also check full text if not found in individual lines
        if (catalogNumber.isBlank()) {
            val match = catalogPattern.find(fullText)
            if (match != null) catalogNumber = match.value.trim()
        }

        // Find artist and title — heuristic approach:
        // Artist lines often contain "by", or are shorter lines
        // Title lines are usually the longest non-label, non-catalog lines
        val nonMetaLines = lines.filter { line ->
            line != labelName &&
            line != catalogNumber &&
            line.length > 2 &&
            !line.matches(Regex("\\d+ rpm|78 RPM|78rpm|Long Play|Microgroove", RegexOption.IGNORE_CASE))
        }

        var artist = ""
        var title = ""

        if (nonMetaLines.isNotEmpty()) {
            // Heuristic: the longest line is often the title
            // Lines with "by" or shorter lines are often the artist
            val sorted = nonMetaLines.sortedByDescending { it.length }
            title = sorted.firstOrNull() ?: ""

            // Look for artist
            for (line in nonMetaLines) {
                if (line.contains(" by ", ignoreCase = true)) {
                    val byIndex = line.indexOf(" by ", ignoreCase = true)
                    val afterBy = line.substring(byIndex + 4).trim()
                    if (afterBy.isNotBlank()) {
                        artist = afterBy
                        // The part before "by" might be the title
                        val beforeBy = line.substring(0, byIndex).trim()
                        if (beforeBy.isNotBlank() && beforeBy.length > title.length) {
                            title = beforeBy
                        }
                        break
                    }
                }
            }

            // If no "by" found, try second longest line as artist
            if (artist.isBlank() && nonMetaLines.size >= 2) {
                // Filter out the title line
                val candidates = nonMetaLines.filter { it != title }
                artist = candidates.maxByOrNull { it.length } ?: ""
            }
        }

        return ParsedRecord(
            catalogNumber = catalogNumber,
            artist = artist,
            title = title,
            labelName = labelName
        )
    }

    private suspend fun loadBitmap(path: String): Bitmap? {
        return try {
            val uri = Uri.parse(path)
            val inputStream = if (path.startsWith("content://")) {
                // Content URI — would need context in production
                null
            } else {
                java.io.FileInputStream(path)
            }
            inputStream?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap", e)
            null
        }
    }

    private suspend inline fun <T> withIO(crossinline block: suspend () -> T): T {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
    }

    companion object {
        private const val TAG = "OcrService"
    }
}

private data class ParsedRecord(
    val catalogNumber: String,
    val artist: String,
    val title: String,
    val labelName: String
)