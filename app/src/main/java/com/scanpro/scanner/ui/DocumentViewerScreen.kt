package com.scanpro.scanner.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanpro.scanner.data.ScannedDocument
import com.scanpro.scanner.util.DocumentUtils
import com.scanpro.scanner.util.OcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerScreen(
    document: ScannedDocument,
    onBack: () -> Unit,
    onRenameClick: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onDeleteClick: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val pages = document.pageFiles
    val pagerState = rememberPagerState(pageCount = { maxOf(pages.size, 1) })

    var showMenu by remember { mutableStateOf(false) }
    var ocrLoading by remember { mutableStateOf(false) }
    var extractedText by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${document.pageCount} page${if (document.pageCount == 1) "" else "s"} • ${DocumentUtils.formatFileSize(document.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRenameClick) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Rename")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open in External PDF App") },
                                leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, null) },
                                onClick = {
                                    showMenu = false
                                    DocumentUtils.openPdfExternally(context, document)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Current Page (JPG)") },
                                leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                onClick = {
                                    showMenu = false
                                    val currentFile = pages.getOrNull(pagerState.currentPage)
                                    if (currentFile != null) {
                                        DocumentUtils.shareImage(context, currentFile, document.title)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Print PDF") },
                                leadingIcon = { Icon(Icons.Rounded.Print, null) },
                                onClick = {
                                    showMenu = false
                                    DocumentUtils.printPdf(context, document)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Document", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = { DocumentUtils.sharePdf(context, document) },
                    icon = { Icon(Icons.Rounded.Share, contentDescription = "Share PDF") },
                    label = { Text("Share PDF") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSaveToDownloads,
                    icon = { Icon(Icons.Rounded.Download, contentDescription = "Save") },
                    label = { Text("Save PDF") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        val currentPage = pages.getOrNull(pagerState.currentPage)
                        if (currentPage == null) {
                            onShowMessage("No page image available for OCR.")
                        } else {
                            ocrLoading = true
                            OcrHelper.extractTextFromImage(
                                context = context,
                                imageFile = currentPage,
                                onSuccess = { text ->
                                    ocrLoading = false
                                    extractedText = text
                                },
                                onError = { msg ->
                                    ocrLoading = false
                                    onShowMessage(msg)
                                }
                            )
                        }
                    },
                    icon = { Icon(Icons.Rounded.TextFields, contentDescription = "OCR") },
                    label = { Text("OCR Text") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { DocumentUtils.printPdf(context, document) },
                    icon = { Icon(Icons.Rounded.Print, contentDescription = "Print") },
                    label = { Text("Print") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF1C2122))
        ) {
            if (pages.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("PDF ready to share or open", color = Color.White)
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    ZoomablePageImage(file = pages[pageIndex])
                }

                // Page Indicator Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Page ${pagerState.currentPage + 1} of ${pages.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            if (ocrLoading) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Extracting text (OCR)...")
                    }
                }
            }
        }
    }

    // OCR Result Dialog
    extractedText?.let { text ->
        AlertDialog(
            onDismissRequest = { extractedText = null },
            title = {
                Text(
                    "Extracted Text (Page ${pagerState.currentPage + 1})",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Scanned Text", text))
                        onShowMessage("Text copied to clipboard")
                        extractedText = null
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy All")
                }
            },
            dismissButton = {
                TextButton(onClick = { extractedText = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun ZoomablePageImage(file: File) {
    var bitmap by remember(file.absolutePath) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(file.absolutePath) { mutableFloatStateOf(1f) }
    var offset by remember(file.absolutePath) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(file.absolutePath) {
        bitmap = withContext(Dispatchers.IO) {
            DocumentUtils.decodeSampledBitmap(file, 1600, 2200)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .pointerInput(file.absolutePath) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = newScale
                    offset = if (newScale > 1f) {
                        Offset(offset.x + pan.x, offset.y + pan.y)
                    } else {
                        Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded == null) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = "Scanned page",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        }
    }
}
