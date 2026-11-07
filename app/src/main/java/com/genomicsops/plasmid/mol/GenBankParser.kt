package com.genomicsops.plasmid.mol

import java.io.InputStream

data class Feature(
    val name: String,
    val type: String,
    val start: Int,
    val end: Int,
    val strand: String, // "+", "-", or "."
    val color: Int
)

data class Plasmid(
    val name: String,
    val sequence: String,
    val features: List<Feature>
) {
    val length: Int get() = sequence.length
}

object GenBankParser {
    private val featureColors = intArrayOf(
        0xFF00E5FF.toInt(), 0xFF00FFAA.toInt(), 0xFFFF0055.toInt(),
        0xFFFFAA00.toInt(), 0xFFAA00FF.toInt(), 0xFF5555FF.toInt()
    )

    fun parse(inputStream: InputStream): Plasmid? {
        val lines = inputStream.bufferedReader().readLines()
        var name = "Unknown Plasmid"
        var sequenceBuilder = StringBuilder()
        val features = mutableListOf<Feature>()

        var inFeatures = false
        var inOrigin = false
        
        var currentFeatureType = ""
        var currentLocation = ""
        var currentName = ""
        var currentColorIndex = 0

        for (line in lines) {
            if (line.startsWith("LOCUS")) {
                name = line.substring(12, 28).trim()
            } else if (line.startsWith("FEATURES")) {
                inFeatures = true
                inOrigin = false
            } else if (line.startsWith("ORIGIN")) {
                inFeatures = false
                inOrigin = true
            } else if (inFeatures) {
                // A new feature key starts at column 6 (index 5) and is not a space
                if (line.length > 20 && line[5] != ' ') {
                    if (currentFeatureType.isNotEmpty() && currentLocation.isNotEmpty()) {
                        val (start, end, strand) = parseLocation(currentLocation)
                        if (start > 0 && end >= start) {
                            features.add(Feature(currentName.ifEmpty { currentFeatureType }, currentFeatureType, start, end, strand, featureColors[currentColorIndex % featureColors.size]))
                            currentColorIndex++
                        }
                    }
                    val parts = line.trim().split("\\s+".toRegex(), limit = 2)
                    currentFeatureType = parts[0]
                    currentLocation = if (parts.size > 1) parts[1] else ""
                    currentName = ""
                } else if (line.length > 21 && line[5] == ' ') {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("/gene=") || trimmed.startsWith("/label=")) {
                        if (currentName.isEmpty()) currentName = trimmed.substringAfter("=").replace("\"", "")
                    } else if (trimmed.startsWith("/product=") && currentName.isEmpty()) {
                        currentName = trimmed.substringAfter("=").replace("\"", "")
                    } else if (!trimmed.startsWith("/")) {
                        // Continuation of location string (e.g. join spanning lines)
                        currentLocation += trimmed
                    }
                }
            } else if (inOrigin) {
                val seq = line.filter { it.isLetter() }
                sequenceBuilder.append(seq)
            }
        }
        
        // Add the very last feature
        if (currentFeatureType.isNotEmpty() && currentLocation.isNotEmpty()) {
            val (start, end, strand) = parseLocation(currentLocation)
            if (start > 0 && end >= start) {
                features.add(Feature(currentName.ifEmpty { currentFeatureType }, currentFeatureType, start, end, strand, featureColors[currentColorIndex % featureColors.size]))
            }
        }

        return if (sequenceBuilder.isNotEmpty()) {
            Plasmid(name, sequenceBuilder.toString().uppercase(), features)
        } else null
    }

    private fun parseLocation(loc: String): Triple<Int, Int, String> {
        val isComplement = loc.contains("complement")
        val strand = if (isComplement) "-" else "+"
        
        // Extract all numbers, ignoring non-digits and dots
        val numbers = loc.filter { it.isDigit() || it == '.' }.split("..")
        val validNums = numbers.mapNotNull { it.toIntOrNull() }
        
        return if (validNums.size >= 2) {
            Triple(validNums.first(), validNums.last(), strand)
        } else {
            Triple(0, 0, strand)
        }
    }
}
