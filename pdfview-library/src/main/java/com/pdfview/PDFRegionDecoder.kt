package com.pdfview

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Size
import androidx.annotation.ColorInt
import com.pdfview.subsamplincscaleimageview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.pdfview.subsamplincscaleimageview.decoder.ImageRegionDecoder
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

internal class PDFRegionDecoder(private val view: PDFView,
                                private val file: File,
                                private val scale: Float,
                                @param:ColorInt private val backgroundColorPdf: Int = Color.WHITE) : ImageRegionDecoder {

    private lateinit var descriptor: ParcelFileDescriptor
    private var renderer: PdfRenderer? = null
    private var firstPageWidth = 0
    private var firstPageHeight = 0
    private var pageSizes: List<Size>? = null

    @Throws(Exception::class)
    override fun init(context: Context, uri: Uri): Point {
        descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(descriptor)

        synchronized(renderer!!) {
            val firstPage = renderer!!.openPage(0)
            firstPage.use {
                firstPageWidth = (firstPage.width * scale).toInt()
                firstPageHeight = (firstPage.height * scale).toInt()
                if (renderer!!.pageCount > 15) {
                    view.setHasBaseLayerTiles(false)
                } else if (renderer!!.pageCount == 1) {
                    view.setMinimumScaleType(SCALE_TYPE_CENTER_INSIDE)
                }
            }

            pageSizes = generateSequence(0, { it + 1 }).take(getPageCount())
                    .map {
                        val page = renderer!!.openPage(it)
                        page.use {
                            Size(page.width, page.height)
                        }
                    }
                    .toList()
        }

        val maxWidth = pageSizes!!.maxOf { size -> size.width } * scale
        val totalHeight = pageSizes!!.asSequence().map { size -> size.height }.sum() * scale
        return Point(maxWidth.toInt(), totalHeight.toInt())
    }

    override fun decodeRegion(rect: Rect, sampleSize: Int): Bitmap {
        val numPageAtStart = floor(rect.top.toDouble() / firstPageHeight).toInt()
        val numPageAtEnd = ceil(rect.bottom.toDouble() / firstPageHeight).toInt() - 1
        val bitmap = Bitmap.createBitmap(rect.width() / sampleSize, rect.height() / sampleSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColorPdf)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        for ((iteration, pageIndex) in (numPageAtStart..numPageAtEnd).withIndex()) {
            synchronized(renderer!!) {
                val page = renderer!!.openPage(pageIndex)
                page.use {
                    val matrix = Matrix()
                    matrix.setScale(scale / sampleSize, scale / sampleSize)
                    val dx = (-rect.left / sampleSize).toFloat()
                    val dy = -((rect.top - firstPageHeight * numPageAtStart) / sampleSize).toFloat() + (firstPageHeight.toFloat() / sampleSize) * iteration
                    matrix.postTranslate(dx, dy)
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
        return bitmap
    }

    override fun isReady(): Boolean {
        return firstPageWidth > 0 && firstPageHeight > 0
    }

    override fun recycle() {
        if (renderer != null) {
            renderer!!.close()
        }
        descriptor.close()
        firstPageWidth = 0
        firstPageHeight = 0
        pageSizes = null
    }

    fun getPageHeight(): Int {
        return firstPageHeight
    }

    fun getPageCount(): Int {
        return try {
            if (renderer == null) 0 else renderer!!.pageCount
        } catch (e: IllegalStateException) {
            // this may happen if the Renderer already got closed
            0
        }
    }
}
