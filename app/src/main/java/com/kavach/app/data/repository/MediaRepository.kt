package com.kavach.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.kavach.app.data.remote.api.KavachApiV2
import com.kavach.app.data.remote.dto.v2.FieldDataDto
import com.kavach.app.utils.ApiResult
import com.kavach.app.utils.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: KavachApiV2
) {

    /**
     * Compresses and uploads an image as mission evidence.
     */
    suspend fun uploadEvidence(
        title: String,
        imageUri: Uri,
        category: String = "EVIDENCE"
    ): ApiResult<FieldDataDto> {
        return try {
            val file = compressImage(imageUri)
            val filePart = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            )
            val titlePart    = title.toRequestBody("text/plain".toMediaTypeOrNull())
            val categoryPart = category.toRequestBody("text/plain".toMediaTypeOrNull())
            val response     = api.uploadFieldData(filePart, titlePart, categoryPart)
            val body         = response.body()
            if (response.isSuccessful && body?.data != null) {
                ApiResult.Success(body.data)
            } else {
                ApiResult.Error(body?.message ?: "Upload failed", response.code())
            }
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401, 403 -> ApiResult.Unauthorized()
                else     -> ApiResult.Error("Upload failed: HTTP ${e.code()}", e.code(), e)
            }
        } catch (e: java.io.IOException) {
            ApiResult.Offline("Upload failed: no connection")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Upload failed", throwable = e)
        }
    }

    /**
     * Compresses image to avoid memory/bandwidth issues in the field.
     * Uses inSampleSize to downscale before decoding to prevent OOM.
     */
    private fun compressImage(uri: Uri): File {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { 
            BitmapFactory.decodeStream(it, null, options)
        }

        // Calculate scale (target 1280px max dimension)
        val targetSize = 1280
        var inSampleSize = 1
        if (options.outHeight > targetSize || options.outWidth > targetSize) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while (halfHeight / inSampleSize >= targetSize && halfWidth / inSampleSize >= targetSize) {
                inSampleSize *= 2
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize

        val bitmap = context.contentResolver.openInputStream(uri)?.use { 
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw Exception("Failed to decode bitmap")

        val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        val out = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        out.flush()
        out.close()
        bitmap.recycle() // Critical: release native memory
        return file
    }

    /**
     * Prepares an audio file for transmission.
     */
    suspend fun uploadAudioReport(
        title: String,
        audioFile: File
    ): ApiResult<FieldDataDto> {
        return try {
            val filePart = MultipartBody.Part.createFormData(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/mpeg".toMediaTypeOrNull())
            )
            val titlePart    = title.toRequestBody("text/plain".toMediaTypeOrNull())
            val categoryPart = "INTEL".toRequestBody("text/plain".toMediaTypeOrNull())
            val response     = api.uploadFieldData(filePart, titlePart, categoryPart)
            val body         = response.body()
            if (response.isSuccessful && body?.data != null) {
                ApiResult.Success(body.data)
            } else {
                ApiResult.Error(body?.message ?: "Audio upload failed", response.code())
            }
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401, 403 -> ApiResult.Unauthorized()
                else     -> ApiResult.Error("Audio upload failed: HTTP ${e.code()}", e.code(), e)
            }
        } catch (e: java.io.IOException) {
            ApiResult.Offline("Audio upload failed: no connection")
        } catch (e: Exception) {
            ApiResult.Error(e.localizedMessage ?: "Audio upload failed", throwable = e)
        }
    }
}
