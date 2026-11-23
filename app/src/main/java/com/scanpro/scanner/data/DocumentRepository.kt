package com.scanpro.scanner.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(private val context: Context) {

    private val scansRoot: File
        get() = File(context.filesDir, "scans").apply {
            if (!exists()) mkdirs()
        }

    fun getAllDocuments(): List<ScannedDocument> {
        val folders = scansRoot.listFiles { f -> f.isDirectory } ?: return emptyList()
        return folders.mapNotNull { folder -> readDocument(folder) }
            .sortedByDescending { it.createdAt }
    }

    private fun readDocument(folder: File): ScannedDocument? {
        val pdfFile = File(folder, "document.pdf")
        if (!pdfFile.exists()) return null

        val metaFile = File(folder, "meta.json")
        var title = folder.name
        var createdAt = pdfFile.lastModified()
        var pageCount = 1

        if (metaFile.exists()) {
            try {
                val json = JSONObject(metaFile.readText())
                title = json.optString("title", title)
                createdAt = json.optLong("createdAt", createdAt)
                pageCount = json.optInt("pageCount", 1)
            } catch (_: Exception) {
            }
        }

        val pageFiles = folder.listFiles { f ->
            f.isFile && f.name.startsWith("page_") && f.name.endsWith(".jpg")
        }?.sortedBy { file ->
            file.name.removePrefix("page_").removeSuffix(".jpg").toIntOrNull() ?: 0
        } ?: emptyList()

        val actualPageCount = if (pageFiles.isNotEmpty()) pageFiles.size else pageCount

        return ScannedDocument(
            id = folder.name,
            title = title,
            createdAt = createdAt,
            pageCount = actualPageCount,
            fileSizeBytes = pdfFile.length(),
            pdfFile = pdfFile,
            pageFiles = pageFiles,
            thumbnailFile = pageFiles.firstOrNull()
        )
    }

    fun saveScanResult(
        pdfUri: Uri?,
        pageUris: List<Uri>,
        defaultPrefix: String = "Scan"
    ): ScannedDocument? {
        if (pdfUri == null && pageUris.isEmpty()) return null

        val id = "doc_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val folder = File(scansRoot, id).apply { mkdirs() }

        val savedPages = mutableListOf<File>()
        pageUris.forEachIndexed { index, uri ->
            val destPage = File(folder, "page_$index.jpg")
            copyUriToFile(uri, destPage)
            if (destPage.exists() && destPage.length() > 0) {
                savedPages.add(destPage)
            }
        }

        val pdfFile = File(folder, "document.pdf")
        if (pdfUri != null) {
            copyUriToFile(pdfUri, pdfFile)
        }

        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            folder.deleteRecursively()
            return null
        }

        if (savedPages.isEmpty()) {
            renderPdfPagesToJpegs(pdfFile, folder, savedPages)
        }

        val now = System.currentTimeMillis()
        val dateStamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(now))
        val title = "$defaultPrefix $dateStamp"

        val meta = JSONObject().apply {
            put("title", title)
            put("createdAt", now)
            put("pageCount", maxOf(savedPages.size, 1))
        }
        File(folder, "meta.json").writeText(meta.toString())

        return readDocument(folder)
    }

    private fun renderPdfPagesToJpegs(pdfFile: File, folder: File, outList: MutableList<File>) {
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = 2
                            val bitmap = Bitmap.createBitmap(
                                page.width * scale,
                                page.height * scale,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val outFile = File(folder, "page_$i.jpg")
                            FileOutputStream(outFile).use { fos ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                            }
                            bitmap.recycle()
                            outList.add(outFile)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    fun renameDocument(document: ScannedDocument, newTitle: String): ScannedDocument? {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isEmpty()) return document

        val folder = File(scansRoot, document.id)
        val metaFile = File(folder, "meta.json")
        val meta = JSONObject().apply {
            put("title", cleanTitle)
            put("createdAt", document.createdAt)
            put("pageCount", document.pageCount)
        }
        metaFile.writeText(meta.toString())
        return readDocument(folder)
    }

    fun deleteDocument(document: ScannedDocument): Boolean {
        val folder = File(scansRoot, document.id)
        return folder.deleteRecursively()
    }

    fun exportPdfToDownloads(document: ScannedDocument): Result<String> {
        return try {
            val safeName = document.title
                .replace(Regex("[^a-zA-Z0-9._\- ]"), "_")
                .trim()
                .ifEmpty { "ScanPro_Document" }
            val fileName = if (safeName.endsWith(".pdf", ignoreCase = true)) {
                safeName
            } else {
                "$safeName.pdf"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/ScanPro"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(IllegalStateException("Could not create file in Downloads"))

                resolver.openOutputStream(uri)?.use { out ->
                    document.pdfFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("Downloads/ScanPro/$fileName")
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "ScanPro"
                ).apply { mkdirs() }
                val dest = File(downloadsDir, fileName)
                document.pdfFile.copyTo(dest, overwrite = true)
                Result.success(dest.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun copyUriToFile(uri: Uri, destFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
