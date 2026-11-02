package com.alphmedia.app.media.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.alphmedia.app.core.model.TimedTextOverlay
import com.alphmedia.app.core.model.VideoClipSegment
import com.google.common.collect.ImmutableList
import java.io.File

/**
 * Real Hardware-Accelerated Video Export Engine using AndroidX Media3 Transformer.
 * Supports:
 * - Clip trimming (start/end)
 * - Rotation & Horizontal/Vertical Flip
 * - Mute original audio
 * - Timed Text Overlays rendered ONLY during their configured startTimeMs to endTimeMs intervals.
 */
class VideoTransformerEngine(private val context: Context) {

    private var activeTransformer: Transformer? = null

    fun exportEditedVideo(
        segment: VideoClipSegment,
        timedTextOverlays: List<TimedTextOverlay>,
        outputFile: File,
        onCompleted: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val mediaItem = MediaItem.Builder()
            .setUri(segment.sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(segment.trimStartMs.coerceAtLeast(0L))
                    .setEndPositionMs(segment.trimEndMs.coerceAtLeast(segment.trimStartMs + 100L))
                    .build()
            )
            .build()

        val videoEffects = mutableListOf<Effect>()

        // Rotation & Flip Transformation
        if (segment.rotationDegrees != 0 || segment.flipHorizontal || segment.flipVertical) {
            val scaleX = if (segment.flipHorizontal) -1f else 1f
            val scaleY = if (segment.flipVertical) -1f else 1f
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(segment.rotationDegrees.toFloat())
                    .setScale(scaleX, scaleY)
                    .build()
            )
        }

        // Timed Text Overlays on Selected Segments
        if (timedTextOverlays.isNotEmpty()) {
            val timedOverlay = object : BitmapOverlay() {
                private val emptyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                private val frameBitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
                private val canvas = Canvas(frameBitmap)
                private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                }
                private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

                override fun getBitmap(presentationTimeUs: Long): Bitmap {
                    val currentTimeMs = presentationTimeUs / 1000L
                    val activeTexts = timedTextOverlays.filter { it.isActiveAt(currentTimeMs) }
                    if (activeTexts.isEmpty()) return emptyBitmap

                    frameBitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                    activeTexts.forEach { item ->
                        textPaint.color = item.textColorHex.toInt()
                        textPaint.textSize = item.fontSizeSp * 2.2f
                        bgPaint.color = item.backgroundColorHex.toInt()

                        val x = frameBitmap.width * item.normalizedX
                        val y = frameBitmap.height * item.normalizedY
                        val textWidth = textPaint.measureText(item.text)
                        canvas.drawRoundRect(
                            x - textWidth / 2f - 24f,
                            y - textPaint.textSize - 12f,
                            x + textWidth / 2f + 24f,
                            y + 18f,
                            16f,
                            16f,
                            bgPaint
                        )
                        canvas.drawText(item.text, x, y, textPaint)
                    }
                    return frameBitmap
                }
            }
            videoEffects.add(OverlayEffect(ImmutableList.of(timedOverlay)))
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(segment.isMuted)
            .setEffects(Effects(/* audioProcessors = */ emptyList(), videoEffects))
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onCompleted(outputFile)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    onError(exportException.localizedMessage ?: "Video export failed.")
                }
            })
            .build()

        activeTransformer = transformer
        transformer.start(editedMediaItem, outputFile.absolutePath)
    }

    fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
    }
}
