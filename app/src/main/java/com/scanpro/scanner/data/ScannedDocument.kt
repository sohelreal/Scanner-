package com.scanpro.scanner.data

import java.io.File

data class ScannedDocument(
    val id: String,
    val title: String,
    val createdAt: Long,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val pdfFile: File,
    val pageFiles: List<File>,
    val thumbnailFile: File?
)
