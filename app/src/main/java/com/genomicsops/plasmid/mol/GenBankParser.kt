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
        0xFF00E5FF.toInt(), // Cyan
        0xFF00FFAA.toInt(), // Teal
        0xFFFF0055.toInt(), // Pink
        0xFFFFAA00.toInt(), // Orange
        0xFFAA00FF.toInt(), // Purple
        0xFF5555FF.toInt()  // Blue
    )

    fun parse(inputStream: InputStream): Plasmid? {
        val lines = inputStream.bufferedReader().readLines()
        var name = "Unknown Plasmid"
        var sequenceBuilder = StringBuilder()
        val features = mutableListOf<Feature>()

        var inFeatures = false
        var inOrigin = false
        var currentFeatureStart = 0
        var currentFeatureEnd = 0
        var currentFeatureType = ""
        var currentFeatureStrand = "+"
        var currentFeatureName = ""
        var currentFeatureColorIndex = 0

        for (line in lines) {
            if (line.startsWith("LOCUS")) {
                name = line.substring(12, 28).trim()
            } else if (line.startsWith("FEATURES")) {
                inFeatures = true
                inOrigin = false
            } else if (line.startsWith("ORIGIN")) {
                inFeatures = false
                inOrigin = true
            } else if (inFeatures && line.length > 21 && (line[5] != ' ' || line[20] != ' ')) {
                // Parsing a new feature
                if (currentFeatureType.isNotEmpty()) {
                    features.add(Feature(currentFeatureName.ifEmpty { currentFeatureType }, currentFeatureType, currentFeatureStart, currentFeatureEnd, currentFeatureStrand, featureColors[currentFeatureColorIndex % featureColors.size]))
                    currentFeatureColorIndex++
                }
                
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.isNotEmpty()) {
                    currentFeatureType = parts[0]
                    val location = parts.getOrElse(1) { "" }
                    
                    val isComplement = location.contains("complement")
                    currentFeatureStrand = if (isComplement) "-" else "+"
                    
                    val nums = location.filter { it.isDigit() || it == '.' }.split("..")
                    if (nums.size >= 2) {
                        currentFeatureStart = nums[0].toIntOrNull() ?: 0
                        currentFeatureEnd = nums[1].toIntOrNull() ?: 0
                    }
                    currentFeatureName = ""
                }
            } else if (inFeatures && line.trim().startsWith("/label=")) {
                currentFeatureName = line.trim().removePrefix("/label=").replace("\"", "")
            } else if (inFeatures && line.trim().startsWith("/gene=")) {
                if (currentFeatureName.isEmpty()) currentFeatureName = line.trim().removePrefix("/gene=").replace("\"", "")
            } else if (inOrigin) {
                val seq = line.filter { it.isLetter() }
                sequenceBuilder.append(seq)
            }
        }
        
        // Add the last feature
        if (currentFeatureType.isNotEmpty()) {
            features.add(Feature(currentFeatureName.ifEmpty { currentFeatureType }, currentFeatureType, currentFeatureStart, currentFeatureEnd, currentFeatureStrand, featureColors[currentFeatureColorIndex % featureColors.size]))
        }

        return if (sequenceBuilder.isNotEmpty()) {
            Plasmid(name, sequenceBuilder.toString().uppercase(), features)
        } else null
    }
}
