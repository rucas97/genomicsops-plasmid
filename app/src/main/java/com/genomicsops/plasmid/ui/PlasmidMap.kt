package com.genomicsops.plasmid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.genomicsops.plasmid.mol.Feature
import com.genomicsops.plasmid.mol.Plasmid
import kotlin.math.*

@Composable
fun PlasmidMap(
    plasmid: Plasmid,
    modifier: Modifier = Modifier,
    selectedFeature: Feature?,
    onFeatureSelected: (Feature?) -> Unit
) {
    var rotation by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotationChange ->
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    rotation += rotationChange
                    rotation += pan.x * 0.1f
                }
            }
            .pointerInput(plasmid, rotation, scale) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val minDim = min(size.width, size.height).toFloat()
                    val baseRadius = (minDim / 2f) * 0.7f * scale
                    
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val dist = sqrt(dx*dx + dy*dy)

                    // If tap is near the circle
                    if (dist > baseRadius * 0.7f && dist < baseRadius * 1.3f) {
                        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        angle = (angle - rotation + 360f) % 360f
                        
                        // Find which feature is at this angle
                        val tappedFeature = plasmid.features.find { feature ->
                            val startAngle = (feature.start.toFloat() / plasmid.length) * 360f
                            val sweepAngle = ((feature.end - feature.start).toFloat() / plasmid.length) * 360f
                            angle >= startAngle && angle <= (startAngle + sweepAngle)
                        }
                        
                        if (tappedFeature != null) {
                            onFeatureSelected(tappedFeature)
                            onBackboneSelected(false)
                        } else {
                            // They tapped the ring, but not on a feature. This is the backbone!
                            onFeatureSelected(null)
                            onBackboneSelected(true)
                        }
                    } else {
                        onFeatureSelected(null) // Tapped empty space
                    }
                }
            }
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val minDim = min(size.width, size.height).toFloat()
                    val baseRadius = (minDim / 2f) * 0.7f * scale
        val strokeWidth = baseRadius * 0.08f

        // 1. Draw Backbone
        drawCircle(
            color = GenomicsSurface,
            radius = baseRadius,
            center = center,
            style = Stroke(width = strokeWidth * 2f)
        )
        drawCircle(
            color = GenomicsMuted.copy(alpha = 0.3f),
            radius = baseRadius,
            center = center,
            style = Stroke(width = 2f)
        )

        // 2. Draw Features
        rotate(rotation, center) {
            plasmid.features.forEach { feature ->
                val startAngle = (feature.start.toFloat() / plasmid.length) * 360f - 90f
                val sweepAngle = ((feature.end - feature.start).toFloat() / plasmid.length) * 360f
                
                val isSelected = feature == selectedFeature
                val radius = if (isSelected) baseRadius * 1.15f else baseRadius
                val width = if (isSelected) strokeWidth * 1.5f else strokeWidth
                val alpha = if (selectedFeature == null || isSelected) 1f else 0.3f

                drawArc(
                    color = Color(feature.color).copy(alpha = alpha),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = width)
                )

                // Draw an arrowhead if it's a gene/CDS
                if (feature.type == "CDS" || feature.type == "gene") {
                    val endAngleRad = Math.toRadians((startAngle + sweepAngle).toDouble())
                    val arrowX = center.x + radius * cos(endAngleRad).toFloat()
                    val arrowY = center.y + radius * sin(endAngleRad).toFloat()
                    drawCircle(
                        color = Color(feature.color).copy(alpha = alpha),
                        radius = width / 1.5f,
                        center = Offset(arrowX, arrowY)
                    )
                }
            }
        }
    }
}
