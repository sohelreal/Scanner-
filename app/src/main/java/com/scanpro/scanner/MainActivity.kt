package com.scanpro.scanner

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.scanpro.scanner.data.DocumentRepository
import com.scanpro.scanner.data.ScannedDocument
import com.scanpro.scanner.ui.DocumentViewerScreen
import com.scanpro.scanner.ui.theme.ScanProTheme
import com.scanpro.scanner.util.DocumentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScanProTheme {
                ScanProApp(activity = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanProApp(activity: Activity) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var documents by remember { mutableStateOf<List<ScannedDocument>>(emptyList()) }
    var selectedDocument by remember { mutableStateOf<ScannedDocument?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }
    var pendingPrefix by remember { mutableStateOf("Scan") }

    var docToRename by remember { mutableStateOf<ScannedDocument?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var docToDelete by remember { mutableStateOf<ScannedDocument?>(null) }

    fun reloadDocuments() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { repository.getAllDocuments() }
            documents = loaded
            selectedDocument?.let { current ->
                selectedDocument = loaded.find { it.id == current.id }
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadDocuments()
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scanResult?.pdf?.uri
            val pageUris = scanResult?.pages?.map { it.imageUri } ?: emptyList()

            scope.launch {
                val savedDoc = withContext(Dispatchers.IO) {
                    repository.saveScanResult(pdfUri, pageUris, pendingPrefix)
                }
                if (savedDoc != null) {
                    reloadDocuments()
                    selectedDocument = savedDoc
                    snackbarHostState.showSnackbar("Saved "${savedDoc.title}"")
                } else {
                    snackbarHostState.showSnackbar("Could not save scanned document.")
                }
            }
        }
    }

    fun launchDocumentScanner(
        prefix: String = "Scan",
        pageLimit: Int = 100,
        allowGallery: Boolean = true
    ) {
        pendingPrefix = prefix
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(allowGallery)
            .setPageLimit(pageLimit)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Scanner unavailable: ${e.localizedMessage ?: "Ensure Google Play Services is updated."}"
                    )
                }
            }
    }

    val currentDoc = selectedDocument
    if (currentDoc != null) {
        BackHandler {
            selectedDocument = null
        }
        DocumentViewerScreen(
            document = currentDoc,
            onBack = { selectedDocument = null },
            onRenameClick = {
                docToRename = currentDoc
                renameInput = currentDoc.title
            },
            onSaveToDownloads = {
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        repository.exportPdfToDownloads(currentDoc)
                    }
                    result.onSuccess { path ->
                        snackbarHostState.showSnackbar("Saved PDF to $path")
                    }.onFailure { err ->
                        snackbarHostState.showSnackbar("Export failed: ${err.localizedMessage}")
                    }
                }
            },
            onDeleteClick = {
                docToDelete = currentDoc
            },
            onShowMessage = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    } else {
        val filteredDocs = remember(documents, searchQuery) {
            if (searchQuery.isBlank()) documents
            else documents.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        if (isSearching) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        "Search documents...",
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedBorderColor = Color.White.copy(alpha = 0.8f),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_scanner),
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "ScanPro",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isSearching = !isSearching
                            if (!isSearching) searchQuery = ""
                        }) {
                            Icon(
                                painter = painterResource(
                                    if (isSearching) R.drawable.ic_close else R.drawable.ic_search
                                ),
                                contentDescription = "Search"
                            )
                        }
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                painter = painterResource(
                                    if (isGridView) R.drawable.ic_view_list else R.drawable.ic_grid_view
                                ),
                                contentDescription = "Switch View"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { launchDocumentScanner(prefix = "Scan", pageLimit = 100, allowGallery = true) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(painterResource(R.drawable.ic_camera), contentDescription = "Scan") },
                    text = { Text("Scan", fontWeight = FontWeight.SemiBold) }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                QuickActionsBar(
                    onSmartScan = { launchDocumentScanner(prefix = "Scan", pageLimit = 100, allowGallery = true) },
                    onIdCardScan = { launchDocumentScanner(prefix = "ID Card", pageLimit = 2, allowGallery = true) },
                    onImportPhotos = { launchDocumentScanner(prefix = "Imported PDF", pageLimit = 50, allowGallery = true) }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Scans (${filteredDocs.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Ad-Free • Local PDF",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (filteredDocs.isEmpty()) {
                    EmptyStateView(
                        isSearch = searchQuery.isNotBlank(),
                        onStartScan = { launchDocumentScanner(prefix = "Scan") }
                    )
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 4.dp,
                            bottom = 96.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredDocs, key = { it.id }) { doc ->
                            DocumentGridCard(
                                document = doc,
                                onClick = { selectedDocument = doc },
                                onShare = { DocumentUtils.sharePdf(context, doc) },
                                onRename = {
                                    docToRename = doc
                                    renameInput = doc.title
                                },
                                onSaveToDownloads = {
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            repository.exportPdfToDownloads(doc)
                                        }
                                        res.onSuccess { p ->
                                            snackbarHostState.showSnackbar("Saved PDF to $p")
                                        }.onFailure { e ->
                                            snackbarHostState.showSnackbar("Export failed: ${e.localizedMessage}")
                                        }
                                    }
                                },
                                onDelete = { docToDelete = doc }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = 4.dp,
                            bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredDocs, key = { it.id }) { doc ->
                            DocumentListCard(
                                document = doc,
                                onClick = { selectedDocument = doc },
                                onShare = { DocumentUtils.sharePdf(context, doc) },
                                onRename = {
                                    docToRename = doc
                                    renameInput = doc.title
                                },
                                onSaveToDownloads = {
                                    scope.launch {
                                        val res = withContext(Dispatchers.IO) {
                                            repository.exportPdfToDownloads(doc)
                                        }
                                        res.onSuccess { p ->
                                            snackbarHostState.showSnackbar("Saved PDF to $p")
                                        }.onFailure { e ->
                                            snackbarHostState.showSnackbar("Export failed: ${e.localizedMessage}")
                                        }
                                    }
                                },
                                onDelete = { docToDelete = doc }
                            )
                        }
                    }
                }
            }
        }
    }

    docToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { docToRename = null },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Document name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updatedName = renameInput.trim()
                        if (updatedName.isNotEmpty()) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    repository.renameDocument(target, updatedName)
                                }
                                reloadDocuments()
                                snackbarHostState.showSnackbar("Renamed to "$updatedName"")
                            }
                        }
                        docToRename = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { docToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    docToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("Delete Document?") },
            text = {
                Text("Are you sure you want to permanently delete "${target.title}"?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                repository.deleteDocument(target)
                            }
                            if (selectedDocument?.id == target.id) {
                                selectedDocument = null
                            }
                            reloadDocuments()
                            snackbarHostState.showSnackbar("Document deleted")
                        }
                        docToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QuickActionsBar(
    onSmartScan: () -> Unit,
    onIdCardScan: () -> Unit,
    onImportPhotos: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionItem(
                iconRes = R.drawable.ic_scanner,
                label = "Smart Scan",
                tint = Color(0xFF00897B),
                onClick = onSmartScan
            )
            QuickActionItem(
                iconRes = R.drawable.ic_badge,
                label = "ID Card",
                tint = Color(0xFF1E88E5),
                onClick = onIdCardScan
            )
            QuickActionItem(
                iconRes = R.drawable.ic_photo_library,
                label = "Import PDF",
                tint = Color(0xFFFB8C00),
                onClick = onImportPhotos
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    @DrawableRes iconRes: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DocumentListCard(
    document: ScannedDocument,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocumentThumbnail(
                file = document.thumbnailFile,
                modifier = Modifier
                    .size(width = 58.dp, height = 74.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = DocumentUtils.formatDate(document.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${document.pageCount} ${if (document.pageCount == 1) "Page" else "Pages"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = DocumentUtils.formatFileSize(document.fileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onShare) {
                Icon(
                    painter = painterResource(R.drawable.ic_share),
                    contentDescription = "Share PDF",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "More")
                }
                DocumentOptionsMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    onShare = onShare,
                    onSaveToDownloads = onSaveToDownloads,
                    onRename = onRename,
                    onDelete = onDelete
                )
            }
        }
    }
}

@Composable
private fun DocumentGridCard(
    document: ScannedDocument,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.85f)
            ) {
                DocumentThumbnail(
                    file = document.thumbnailFile,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${document.pageCount}p",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = DocumentUtils.formatFileSize(document.fileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_vert),
                            contentDescription = "More",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DocumentOptionsMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onShare = onShare,
                        onSaveToDownloads = onSaveToDownloads,
                        onRename = onRename,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentOptionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Share PDF") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_share), null) },
            onClick = {
                onDismiss()
                onShare()
            }
        )
        DropdownMenuItem(
            text = { Text("Save to Downloads") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_download), null) },
            onClick = {
                onDismiss()
                onSaveToDownloads()
            }
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_edit), null) },
            onClick = {
                onDismiss()
                onRename()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error)
            },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

@Composable
private fun DocumentThumbnail(file: File?, modifier: Modifier = Modifier) {
    var bitmap by remember(file?.absolutePath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file?.absolutePath) {
        bitmap = if (file != null && file.exists()) {
            withContext(Dispatchers.IO) {
                DocumentUtils.decodeSampledBitmap(file, 300, 400)
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = "Document preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_pdf),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateView(
    isSearch: Boolean,
    onStartScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    if (isSearch) R.drawable.ic_search else R.drawable.ic_description
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (isSearch) "No matching documents" else "No scanned documents yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isSearch) {
                "Try a different search keyword."
            } else {
                "Tap the Scan button below to capture multi-page documents, crop edges automatically, enhance colors, and export clean watermark-free PDFs."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!isSearch) {
            Spacer(Modifier.height(18.dp))
            TextButton(onClick = onStartScan) {
                Icon(painterResource(R.drawable.ic_camera), null)
                Spacer(Modifier.width(8.dp))
                Text("Start First Scan", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
