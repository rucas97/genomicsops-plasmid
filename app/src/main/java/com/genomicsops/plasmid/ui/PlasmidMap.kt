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
    onFeatureSelected: (Feature?) -> Unit,
    onBackboneSelected: (Boolean) -> Unit,
    colorTheme: String = "genomicsops"
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

                    if (dist > baseRadius * 0.7f && dist < baseRadius * 1.3f) {
                        var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        angle = (angle - rotation + 360f) % 360f
                        
                        val tappedFeature = plasmid.features.find { feature ->
                            val startAngle = (feature.start.toFloat() / plasmid.length) * 360f
                            val sweepAngle = ((feature.end - feature.start).toFloat() / plasmid.length) * 360f
                            angle >= startAngle && angle <= (startAngle + sweepAngle)
                        }
                        
                        if (tappedFeature != null) {
                            onFeatureSelected(tappedFeature)
                            onBackboneSelected(false)
                        } else {
                            onFeatureSelected(null)
                            onBackboneSelected(true)
                        }
                    }
                }
            }
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val baseRadius = (size.minDimension / 2f) * 0.7f * scale
        val strokeWidth = baseRadius * 0.08f

        drawCircle(color = GenomicsSurface, radius = baseRadius, center = center, style = Stroke(width = strokeWidth * 2f))
        drawCircle(color = GenomicsMuted.copy(alpha = 0.3f), radius = baseRadius, center = center, style = Stroke(width = 2f))

        rotate(rotation, center) {
            plasmid.features.forEach { feature ->
                val startAngle = (feature.start.toFloat() / plasmid.length) * 360f - 90f
                val sweepAngle = ((feature.end - feature.start).toFloat() / plasmid.length) * 360f
                
                // Dynamic Color Themes
                val featureColor = when(colorTheme) {
                    "rainbow" -> android.graphics.Color.HSVToColor(floatArrayOf((plasmid.features.indexOf(feature).toFloat() / plasmid.features.size.coerceAtLeast(1)) * 360f, 1f, 1f))
                    "pastel" -> android.graphics.Color.HSVToColor(floatArrayOf((plasmid.features.indexOf(feature).toFloat() / plasmid.features.size.coerceAtLeast(1)) * 360f, 0.4f, 1f))
                    "mono" -> if (plasmid.features.indexOf(feature) % 2 == 0) 0xFFEEEEEE.toInt() else 0xFF888888.toInt()
                    else -> feature.color
                }
                
                val isSelected = feature == selectedFeature
                val radius = if (isSelected) baseRadius * 1.15f else baseRadius
                val width = if (isSelected) strokeWidth * 1.5f else strokeWidth
                val alpha = if (selectedFeature == null || isSelected) 1f else 0.3f

                drawArc(
                    color = Color(featureColor).copy(alpha = alpha),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = width)
                )

                if (feature.type == "CDS" || feature.type == "gene") {
                    val endAngleRad = Math.toRadians((startAngle + sweepAngle).toDouble())
                    val arrowX = center.x + radius * cos(endAngleRad).toFloat()
                    val arrowY = center.y + radius * sin(endAngleRad).toFloat()
                    drawCircle(color = Color(featureColor).copy(alpha = alpha), radius = width / 1.5f, center = Offset(arrowX, arrowY))
                }
            }
        }
    }
}
