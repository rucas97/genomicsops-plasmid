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
    colorTheme: String = "genomicsops",
    showEnzymes: Boolean = true // NEW FEATURE
) {
    var rotation by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }

    val currentRotation by rememberUpdatedState(rotation)
    val currentScale by rememberUpdatedState(scale)
    val currentShowEnzymes by rememberUpdatedState(showEnzymes)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val minDim = min(size.width, size.height).toFloat()
                    val baseRadius = (minDim / 2f) * 0.7f * currentScale
                    
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val dist = sqrt(dx*dx + dy*dy)

                    if (dist > baseRadius * 0.6f && dist < baseRadius * 1.4f) {
                        var rawAngle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                        var angle = (rawAngle + 90f - currentRotation + 360f) % 360f
                        
                        val tappedFeature = plasmid.features
                            .filter { it.type != "source" } // Ignore full-plasmid source feature
                            .find { feature ->
                            if (feature.type == "enzyme" && !currentShowEnzymes) return@find false
                            
                            val startAngle = (feature.start.toFloat() / plasmid.length) * 360f
                            val sweepAngle = ((feature.end - feature.start).toFloat() / plasmid.length) * 360f
                            val padding = if (feature.type == "enzyme") 5f else 1f
                            
                            if (sweepAngle < 0) {
                                angle >= (startAngle - padding) || angle <= (startAngle + sweepAngle + padding)
                            } else {
                                angle >= (startAngle - padding) && angle <= (startAngle + sweepAngle + padding)
                            }
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
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotationChange ->
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    rotation += rotationChange
                    rotation += pan.x * 0.1f
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
                if (feature.type == "source") return@forEach // Don't draw source
                if (feature.type == "enzyme" && !showEnzymes) return@forEach

                val startAngle = (feature.start.toFloat() / plasmid.length) * 360f - 90f
                val sweepAngle = ((feature.end - feature.start).toFloat() / plasmid.length) * 360f
                
                val featureColor = when(colorTheme) {
                    "rainbow" -> android.graphics.Color.HSVToColor(floatArrayOf((plasmid.features.indexOf(feature).toFloat() / plasmid.features.size.coerceAtLeast(1)) * 360f, 1f, 1f))
                    "pastel" -> android.graphics.Color.HSVToColor(floatArrayOf((plasmid.features.indexOf(feature).toFloat() / plasmid.features.size.coerceAtLeast(1)) * 360f, 0.4f, 1f))
                    "mono" -> if (plasmid.features.indexOf(feature) % 2 == 0) 0xFFEEEEEE.toInt() else 0xFF888888.toInt()
                    else -> feature.color
                }
                
                val isSelected = feature == selectedFeature
                val radius = if (isSelected) baseRadius * 1.15f else baseRadius
                val alpha = if (selectedFeature == null || isSelected) 1f else 0.3f

                if (feature.type == "enzyme") {
                    val radAngle = Math.toRadians((startAngle + 90f).toDouble())
                    val x1 = center.x + (radius - strokeWidth * 1.5f) * cos(radAngle).toFloat()
                    val y1 = center.y + (radius - strokeWidth * 1.5f) * sin(radAngle).toFloat()
                    val x2 = center.x + (radius + strokeWidth * 1.5f) * cos(radAngle).toFloat()
                    val y2 = center.y + (radius + strokeWidth * 1.5f) * sin(radAngle).toFloat()
                    
                    drawLine(
                        color = Color.Red.copy(alpha = alpha),
                        start = Offset(x1, y1),
                        end = Offset(x2, y2),
                        strokeWidth = if (isSelected) 8f else 4f
                    )
                } else {
                    val width = if (isSelected) strokeWidth * 1.5f else strokeWidth
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
}
