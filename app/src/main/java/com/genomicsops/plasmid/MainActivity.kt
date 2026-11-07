package com.genomicsops.plasmid

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genomicsops.plasmid.mol.Feature
import com.genomicsops.plasmid.mol.GenBankParser
import com.genomicsops.plasmid.mol.Plasmid
import com.genomicsops.plasmid.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GenomicsTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) {
        SplashScreen { showSplash = false }
    } else {
        PlasmidScreen()
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex != -1) name = cursor.getString(nameIndex)
    }
    return name
}

@Composable
fun PlasmidScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    var plasmid by remember { mutableStateOf<Plasmid?>(null) }
    var selectedFeature by remember { mutableStateOf<Feature?>(null) }
    var showBackbone by remember { mutableStateOf(false) }
    var colorTheme by remember { mutableStateOf("genomicsops") }
    var isLoading by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            isLoading = true
            scope.launch {
                val parsed = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { GenBankParser.parse(it) }
                    } catch (e: Exception) { null }
                }
                plasmid = parsed
                isLoading = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(GenomicsDark)) {
        if (plasmid == null) {
            // --- HOME SCREEN ---
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Plasmid Viewer", color = GenomicsCyan, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(48.dp))
                Card(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    colors = CardDefaults.cardColors(containerColor = GenomicsSurface),
                    border = BorderStroke(1.dp, Brush.linearGradient(listOf(GenomicsCyan, GenomicsTeal))),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = GenomicsCyan, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(if (isLoading) "Parsing..." else "Select .gb / .gbk File", color = GenomicsText, fontWeight = FontWeight.Medium)
                    }
                }
            }
        } else {
            // --- VIEWER SCREEN ---
            val p = plasmid!!
            
            // Top Bar
            Surface(color = GenomicsSurface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(p.name, color = GenomicsText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${p.length} bp • ${p.features.size} features", color = GenomicsMuted, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { 
                            clipboardManager.setText(AnnotatedString(p.sequence))
                            Toast.makeText(context, "Full sequence copied!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy All", tint = GenomicsTeal)
                        }
                        IconButton(onClick = { 
                            colorTheme = when(colorTheme) {
                                "genomicsops" -> "rainbow"
                                "rainbow" -> "pastel"
                                "pastel" -> "mono"
                                else -> "genomicsops"
                            }
                        }) {
                            Icon(Icons.Default.Palette, contentDescription = "Colors", tint = GenomicsCyan)
                        }
                        TextButton(onClick = { plasmid = null; selectedFeature = null; showBackbone = false }) {
                            Text("Close", color = GenomicsMuted)
                        }
                    }
                }
            }
            // 3D Map
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PlasmidMap(
                    plasmid = p, 
                    selectedFeature = selectedFeature, 
                    onFeatureSelected = { selectedFeature = it }, 
                    onBackboneSelected = { showBackbone = it }, 
                    colorTheme = colorTheme
                )
            }

            // Bottom Details / List Panel
            Surface(
                color = GenomicsSurface,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                if (showBackbone) {
                    // --- FULL PLASMID BACKBONE DETAILS ---
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, color = GenomicsCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            IconButton(onClick = { 
                                clipboardManager.setText(AnnotatedString(p.sequence))
                                Toast.makeText(context, "Full sequence copied!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Full", tint = GenomicsTeal)
                            }
                        }
                        Text("Total length: ${p.length} bp • GC Content: ${String.format("%.1f", p.gcContent)}%", color = GenomicsMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = p.sequence,
                            color = GenomicsText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.background(GenomicsDark, RoundedCornerShape(8.dp)).padding(12.dp).fillMaxWidth()
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showBackbone = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Back to Feature List", color = GenomicsMuted)
                        }
                    }
                } else if (selectedFeature != null) {
                    // --- FEATURE DETAILS ---
                    val feature = selectedFeature!!
                    val featureSeq = p.sequence.substring((feature.start - 1).coerceAtLeast(0), feature.end.coerceAtMost(p.length))
                    
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(feature.name, color = Color(feature.color), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Row {
                                IconButton(onClick = { 
                                    clipboardManager.setText(AnnotatedString(featureSeq))
                                    Toast.makeText(context, "Feature sequence copied!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Feature", tint = GenomicsTeal)
                                }
                                TextButton(onClick = { selectedFeature = null }) {
                                    Text("Close", color = GenomicsMuted, fontSize = 12.sp)
                                }
                            }
                        }
                        Text("${feature.type} • Strand ${feature.strand} • ${feature.start}..${feature.end}", color = GenomicsMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = featureSeq,
                            color = GenomicsText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.background(GenomicsDark, RoundedCornerShape(8.dp)).padding(12.dp).fillMaxWidth()
                        )
                    }
                } else {
                    // --- FEATURE LIST ---
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        items(p.features) { feature ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                                    selectedFeature = feature 
                                    showBackbone = false
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(12.dp).background(Color(feature.color), RoundedCornerShape(50)))
                                Spacer(Modifier.width(12.dp))
                                Text(feature.name, color = GenomicsText, modifier = Modifier.weight(1f))
                                Text("${feature.start}..${feature.end}", color = GenomicsMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
