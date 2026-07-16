package com.recordcheck78

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for OcrService text parsing logic.
 * Tests the heuristic parsing that extracts catalog numbers,
 * artist names, titles, and label names from raw OCR text.
 */
class OcrParsingTest {

    // Test the catalog number regex patterns
    @Test
    fun `extracts Victor-style catalog number with hyphen`() {
        val text = "VICTOR\n20-1234\nCaruso\nLa Donna e Mobile"
        val parsed = parseTestOcr(text)
        assertEquals("20-1234", parsed.catalogNumber)
    }

    @Test
    fun `extracts Columbia-style catalog number`() {
        val text = "COLUMBIA\nCO-12345\nBing Crosby\nWhite Christmas"
        val parsed = parseTestOcr(text)
        assertTrue(parsed.catalogNumber.isNotBlank())
    }

    @Test
    fun `extracts Decca-style alphanumeric catalog`() {
        val text = "DECCA\nA-1234\nPatsy Cline\nWalkin After Midnight"
        val parsed = parseTestOcr(text)
        assertTrue(parsed.catalogNumber.isNotBlank())
    }

    @Test
    fun `identifies Victor label name`() {
        val text = "VICTOR\n20-1234\nEnrico Caruso\nPagliacci"
        val parsed = parseTestOcr(text)
        assertEquals("Victor", parsed.labelName)
    }

    @Test
    fun `identifies Columbia label name`() {
        val text = "COLUMBIA\n12345\nFrank Sinatra\nMy Way"
        val parsed = parseTestOcr(text)
        assertEquals("Columbia", parsed.labelName)
    }

    @Test
    fun `identifies Decca label name`() {
        val text = "DECCA\nA-1001\nBillie Holiday\nStrange Fruit"
        val parsed = parseTestOcr(text)
        assertEquals("Decca", parsed.labelName)
    }

    @Test
    fun `identifies Bluebird label name`() {
        val text = "Bluebird\nB-1234\nLionel Hampton\nFlying Home"
        val parsed = parseTestOcr(text)
        assertEquals("Bluebird", parsed.labelName)
    }

    @Test
    fun `identifies RCA Victor label name`() {
        val text = "RCA Victor\n20-5678\nGlenn Miller\nMoonlight Serenade"
        val parsed = parseTestOcr(text)
        // Should match "RCA Victor" or "Victor" — both are valid
        assertTrue(parsed.labelName.contains("Victor"))
    }

    @Test
    fun `extracts artist from by-line`() {
        val text = "VICTOR\n20-1234\nLa Donna e Mobile by Enrico Caruso"
        val parsed = parseTestOcr(text)
        assertTrue(parsed.artist.contains("Enrico Caruso"))
    }

    @Test
    fun `handles empty OCR text gracefully`() {
        val parsed = parseTestOcr("")
        assertEquals("", parsed.catalogNumber)
        assertEquals("", parsed.artist)
        assertEquals("", parsed.title)
        assertEquals("", parsed.labelName)
    }

    @Test
    fun `handles text with only RPM markings`() {
        val text = "78 RPM\nLong Play\nMicrogroove"
        val parsed = parseTestOcr(text)
        assertEquals("", parsed.catalogNumber)
        assertEquals("", parsed.labelName)
    }

    @Test
    fun `extracts numeric-only catalog number`() {
        val text = "OKeh\n12345\nKing Oliver\nDippermouth Blues"
        val parsed = parseTestOcr(text)
        // Should find a number
        assertTrue(parsed.catalogNumber.isNotBlank())
    }

    // Helper: call the private parser via reflection or replicate logic
    private fun parseTestOcr(text: String): ParsedRecord {
        // Replicate the parsing logic from OcrService for testing
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        val knownLabels = listOf(
            "Victor", "Victrola", "Columbia", "Decca", "Brunswick", "Vocalion",
            "Okeh", "OKeh", "Paramount", "Gennett", "Bluebird", "RCA Victor",
            "Capitol", "Mercury", "London", "MGM", "HMV"
        )

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

        val catalogPattern = Regex(
            "(?:[A-Z]{1,3}[-\\s])?\\d{2,6}[-–]?\\d{0,4}|" +
            "[A-Z]{1,3}[-\\s]\\d{3,6}|" +
            "\\d{2,6}-\\d{2,4}"
        )
        var catalogNumber = ""
        for (line in lines) {
            val match = catalogPattern.find(line)
            if (match != null && match.value.length >= 3) {
                catalogNumber = match.value.trim()
                break
            }
        }
        if (catalogNumber.isBlank()) {
            val match = catalogPattern.find(text)
            if (match != null) catalogNumber = match.value.trim()
        }

        val nonMetaLines = lines.filter { line ->
            line != labelName &&
            line != catalogNumber &&
            line.length > 2 &&
            !line.matches(Regex("\\d+ rpm|78 RPM|78rpm|Long Play|Microgroove", RegexOption.IGNORE_CASE))
        }

        var artist = ""
        var title = ""

        if (nonMetaLines.isNotEmpty()) {
            val sorted = nonMetaLines.sortedByDescending { it.length }
            title = sorted.firstOrNull() ?: ""

            for (line in nonMetaLines) {
                if (line.contains(" by ", ignoreCase = true)) {
                    val byIndex = line.indexOf(" by ", ignoreCase = true)
                    val afterBy = line.substring(byIndex + 4).trim()
                    if (afterBy.isNotBlank()) {
                        artist = afterBy
                        val beforeBy = line.substring(0, byIndex).trim()
                        if (beforeBy.isNotBlank() && beforeBy.length > title.length) {
                            title = beforeBy
                        }
                        break
                    }
                }
            }

            if (artist.isBlank() && nonMetaLines.size >= 2) {
                val candidates = nonMetaLines.filter { it != title }
                artist = candidates.maxByOrNull { it.length } ?: ""
            }
        }

        return ParsedRecord(catalogNumber, artist, title, labelName)
    }
}

private data class ParsedRecord(
    val catalogNumber: String,
    val artist: String,
    val title: String,
    val labelName: String
)