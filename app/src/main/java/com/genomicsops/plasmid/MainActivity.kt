package com.genomicsops.plasmid

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    var plasmid by remember { mutableStateOf<Plasmid?>(null) }
    var selectedFeature by remember { mutableStateOf<Feature?>(null) }
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
                    TextButton(onClick = { plasmid = null; selectedFeature = null }) {
                        Text("Close", color = GenomicsCyan)
                    }
                }
            }

            // 3D Map
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PlasmidMap(plasmid = p, selectedFeature = selectedFeature, onFeatureSelected = { selectedFeature = it })
                
                // Show feature name if selected
                if (selectedFeature != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        color = GenomicsSurface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(selectedFeature!!.color))
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(selectedFeature!!.name, color = Color(selectedFeature!!.color), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Type: ${selectedFeature!!.type} • ${selectedFeature!!.start}..${selectedFeature!!.end}", color = GenomicsText, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Feature List
            Surface(
                color = GenomicsSurface,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(p.features) { feature ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
