package com.example.androidkotlinapp

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

class DocumentViewerActivity : ComponentActivity() {

    companion object {
        private var currentUrl: String? = null

        fun isCurrentlyViewingUrl(url: String): Boolean {
            val cleanUrl = url.trim().lowercase().removePrefix("http://").removePrefix("https://").removeSuffix("/")
            val cleanCurrent = currentUrl?.trim()?.lowercase()?.removePrefix("http://")?.removePrefix("https://")?.removeSuffix("/")
            return cleanCurrent != null && cleanCurrent == cleanUrl
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uri: Uri? = intent.data
        if (uri == null) {
            Toast.makeText(this, "No document URI provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (uri.scheme == "http" || uri.scheme == "https") {
            currentUrl = uri.toString()
        }

        setContent {
            MaterialTheme {
                DocumentViewerScreen(uri = uri, onBack = { finish() })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentUrl = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(uri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    var pdfPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var textContent by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf("Document Viewer") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isWebUrl = uri.scheme == "http" || uri.scheme == "https"

    LaunchedEffect(uri) {
        if (isWebUrl) {
            isLoading = false
            return@LaunchedEffect
        }
        try {
            // Resolve file name safely inside a try-catch to avoid security exceptions
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                fileName = "Document"
            }

            val mimeType = context.contentResolver.getType(uri) ?: ""
            val isPdf = mimeType.contains("pdf") || fileName.endsWith(".pdf", ignoreCase = true)

            // Copy file to local cache first to bypass ContentProvider read permission denials
            val tempFile = java.io.File(context.cacheDir, "temp_view_file")
            if (tempFile.exists()) tempFile.delete()

            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val isTextFile = fileName.endsWith(".csv", ignoreCase = true) || 
                             fileName.endsWith(".txt", ignoreCase = true) ||
                             fileName.endsWith(".log", ignoreCase = true) ||
                             mimeType.contains("text/")

            if (isPdf) {
                // PDF Renderer
                val fileDescriptor = android.os.ParcelFileDescriptor.open(tempFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                if (fileDescriptor != null) {
                    val renderer = PdfRenderer(fileDescriptor)
                    val bitmaps = mutableListOf<Bitmap>()
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val width = 1080
                            val height = (width.toFloat() / page.width * page.height).toInt()
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bitmap)
                        }
                    }
                    pdfPages = bitmaps
                    renderer.close()
                    fileDescriptor.close()
                } else {
                    errorMessage = "Could not open file descriptor for local temp file"
                }
            } else if (isTextFile) {
                // Text/CSV Reader
                tempFile.inputStream().use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val sb = StringBuilder()
                    var line = reader.readLine()
                    var lineCount = 0
                    while (line != null && lineCount < 2000) {
                        sb.append(line).append("\n")
                        line = reader.readLine()
                        lineCount++
                    }
                    textContent = sb.toString()
                }
            } else {
                errorMessage = "This format is not supported by the built-in reader.\n\nPlease open this file using Google Sheets, Microsoft Excel, or another whitelisted application."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = e.localizedMessage ?: "Failed to read document"
        } finally {
            isLoading = false
        }
    }

    if (isWebUrl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(uri.toString(), color = Color.White, fontSize = 14.sp, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF4CAF50)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )
            },
            containerColor = Color.Black
        ) { paddingValues ->
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        webViewClient = android.webkit.WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        loadUrl(uri.toString())
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF4CAF50)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFF4CAF50))
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            } else if (pdfPages.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(pdfPages) { bitmap ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else if (textContent != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    item {
                        Text(
                            text = textContent ?: "",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            } else {
                Text(
                    text = "Unsupported document format",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    }
}
