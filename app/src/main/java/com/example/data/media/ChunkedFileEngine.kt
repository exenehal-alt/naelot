package com.example.data.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.data.model.ChunkTransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.coroutineContext

class ChunkedFileEngine(private val context: Context) {

    companion object {
        const val MAX_FILE_BYTES = 1024L * 1024L * 1024L // Strictly 1GB (1024 MB)
        const val CHUNK_SIZE_BYTES = 5 * 1024 * 1024 // 5MB chunks
    }

    private val vaultDir: File by lazy {
        File(context.filesDir, "circlechat_vault").apply { mkdirs() }
    }

    private val _transferState = MutableStateFlow<ChunkTransferState?>(null)
    val transferState: StateFlow<ChunkTransferState?> = _transferState.asStateFlow()

    private var activeJob: Job? = null

    fun cancelActiveTransfer() {
        activeJob?.cancel()
        _transferState.value = _transferState.value?.copy(isCancelled = true, error = "Upload cancelled")
    }

    fun clearTransferState() {
        _transferState.value = null
    }

    fun attachActiveJob(job: Job) {
        activeJob = job
    }

    data class FileDescriptor(
        val fileName: String,
        val fileSize: Long,
        val mimeType: String
    )

    fun inspectUri(uri: Uri): FileDescriptor {
        val resolver: ContentResolver = context.contentResolver
        var name = "shared_file_${System.currentTimeMillis()}"
        var size = 0L
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        return FileDescriptor(name, size, mime)
    }

    /**
     * Streams any selected file up to 1GB in 5MB chunks, computes SHA-256 integrity hash,
     * and assembles it into persistent local vault storage without loading the file into RAM.
     */
    suspend fun processChunkedUpload(uri: Uri): Result<ChunkTransferState> = withContext(Dispatchers.IO) {
        val descriptor = inspectUri(uri)
        if (descriptor.fileSize > MAX_FILE_BYTES) {
            val err = "File exceeds the 1GB (1024 MB) limit."
            _transferState.value = ChunkTransferState(
                transferId = UUID.randomUUID().toString(),
                fileName = descriptor.fileName,
                mimeType = descriptor.mimeType,
                totalBytes = descriptor.fileSize,
                bytesTransferred = 0L,
                chunkIndex = 0,
                totalChunks = 0,
                speedMbps = 0.0,
                progress = 0f,
                error = err
            )
            return@withContext Result.failure(IllegalArgumentException(err))
        }

        val transferId = UUID.randomUUID().toString().take(12)
        val safeName = descriptor.fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val destFile = File(vaultDir, "${transferId}_$safeName")
        val chunkBuffer = ByteArray(CHUNK_SIZE_BYTES)
        val totalChunks = if (descriptor.fileSize <= 0L) 1 else ((descriptor.fileSize + CHUNK_SIZE_BYTES - 1) / CHUNK_SIZE_BYTES).toInt()
        val digest = MessageDigest.getInstance("SHA-256")
        val startNanos = System.nanoTime()

        try {
            var bytesTransferred = 0L
            var chunkIndex = 0

            _transferState.value = ChunkTransferState(
                transferId = transferId,
                fileName = descriptor.fileName,
                mimeType = descriptor.mimeType,
                totalBytes = descriptor.fileSize,
                bytesTransferred = 0L,
                chunkIndex = 0,
                totalChunks = totalChunks,
                speedMbps = 0.0,
                progress = 0f
            )

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    while (true) {
                        coroutineContext.ensureActive()
                        var chunkFilled = 0
                        while (chunkFilled < CHUNK_SIZE_BYTES) {
                            val read = input.read(chunkBuffer, chunkFilled, CHUNK_SIZE_BYTES - chunkFilled)
                            if (read == -1) break
                            chunkFilled += read
                        }
                        if (chunkFilled <= 0) break

                        output.write(chunkBuffer, 0, chunkFilled)
                        digest.update(chunkBuffer, 0, chunkFilled)
                        bytesTransferred += chunkFilled
                        chunkIndex++

                        val elapsedSec = ((System.nanoTime() - startNanos) / 1_000_000_000.0).coerceAtLeast(0.05)
                        val speedMbps = (bytesTransferred / (1024.0 * 1024.0)) / elapsedSec
                        val progress = if (descriptor.fileSize > 0L) {
                            (bytesTransferred.toFloat() / descriptor.fileSize.toFloat()).coerceIn(0f, 1f)
                        } else 1f

                        _transferState.value = ChunkTransferState(
                            transferId = transferId,
                            fileName = descriptor.fileName,
                            mimeType = descriptor.mimeType,
                            totalBytes = descriptor.fileSize.coerceAtLeast(bytesTransferred),
                            bytesTransferred = bytesTransferred,
                            chunkIndex = chunkIndex,
                            totalChunks = totalChunks.coerceAtLeast(chunkIndex),
                            speedMbps = speedMbps,
                            progress = progress
                        )
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("Cannot open selected file stream."))

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val completed = ChunkTransferState(
                transferId = transferId,
                fileName = descriptor.fileName,
                mimeType = descriptor.mimeType,
                totalBytes = bytesTransferred,
                bytesTransferred = bytesTransferred,
                chunkIndex = chunkIndex.coerceAtLeast(1),
                totalChunks = chunkIndex.coerceAtLeast(1),
                speedMbps = _transferState.value?.speedMbps ?: 0.0,
                progress = 1f,
                isCompleted = true,
                assembledPath = destFile.absolutePath,
                sha256 = sha256
            )
            _transferState.value = completed
            Result.success(completed)
        } catch (e: Exception) {
            destFile.delete()
            _transferState.value = _transferState.value?.copy(
                isCancelled = e is kotlinx.coroutines.CancellationException,
                error = e.localizedMessage ?: "Transfer failed"
            )
            Result.failure(e)
        }
    }

    suspend fun copyMediaToVault(uri: Uri, prefix: String = "media"): Result<Pair<File, FileDescriptor>> =
        withContext(Dispatchers.IO) {
            try {
                val desc = inspectUri(uri)
                val safeName = desc.fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
                val destFile = File(vaultDir, "${prefix}_${System.currentTimeMillis()}_$safeName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Unable to read media."))
                Result.success(destFile to desc)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun encodeCompressedImageBase64(uri: Uri, maxDim: Int = 720, quality: Int = 72): String =
        withContext(Dispatchers.IO) {
            try {
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext ""
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)
                var sampleSize = 1
                while (boundsOpts.outWidth / sampleSize > maxDim * 2 || boundsOpts.outHeight / sampleSize > maxDim * 2) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts) ?: return@withContext ""
                val scale = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height, 1f)
                val finalBmp = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        bmp,
                        (bmp.width * scale).toInt().coerceAtLeast(1),
                        (bmp.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else bmp

                val out = ByteArrayOutputStream()
                finalBmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                var encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (encoded.length > 180_000) {
                    out.reset()
                    finalBmp.compress(Bitmap.CompressFormat.JPEG, 45, out)
                    encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                }
                if (finalBmp !== bmp) bmp.recycle()
                "data:image/jpeg;base64,$encoded"
            } catch (_: Exception) {
                ""
            }
        }

    suspend fun extractVideoMetaAndThumbnail(file: File): Pair<Long, String> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
            val thumbBase64 = if (frame != null) {
                val scale = minOf(480f / frame.width, 480f / frame.height, 1f)
                val scaled = Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 65, out)
                "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            } else ""
            durationMs to thumbBase64
        } catch (_: Exception) {
            0L to ""
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }
}
