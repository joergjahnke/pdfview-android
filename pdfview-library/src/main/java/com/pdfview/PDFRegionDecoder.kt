package com.pdfview

import android.content.Context
import android.graphics.*
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Size
import androidx.annotation.ColorInt
import com.pdfview.subsamplincscaleimageview.SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE
import com.pdfview.subsamplincscaleimageview.decoder.ImageRegionDecoder
import java.io.File

internal class PDFRegionDecoder(
    private val view: PDFView,
    private val file: File,
    private val loadParams: LoadParams? = null,
    private val scale: Float,
    @param:ColorInt private val backgroundColorPdf: Int = Color.WHITE
) : ImageRegionDecoder {

    private lateinit var descriptor: ParcelFileDescriptor
    private var renderer: PdfRenderer? = null
    private var pageSizes: List<Size>? = null

    @Throws(Exception::class)
    override fun init(context: Context, uri: Uri): Point {
        descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = if (loadParams == null || Build.VERSION.SDK_INT < 35) PdfRenderer(descriptor) else PdfRenderer(descriptor, loadParams)

        if (renderer!!.pageCount > 15) {
            view.setHasBaseLayerTiles(false)
        } else if (renderer!!.pageCount == 1) {
            view.setMinimumScaleType(SCALE_TYPE_CENTER_INSIDE)
        }

        synchronized(renderer!!) {
            pageSizes = generateSequence(0) { it + 1 }.take(getPageCount())
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
        val numPageAtStart = calculatePageNoAtScrollPos(rect.top)
        val numPageAtEnd = calculatePageNoAtScrollPos(rect.bottom - 1)
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
                    val dy =
                        -((rect.top - calculateScrollPosForPageNo(numPageAtStart)) / sampleSize).toFloat() + (getPageHeight(0).toFloat() / sampleSize) * iteration
                    matrix.postTranslate(dx, dy)
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
        return bitmap
    }

    override fun isReady(): Boolean {
        return pageSizes != null && pageSizes!![0].width > 0 && pageSizes!![0].height > 0
    }

    @Synchronized
    override fun recycle() {
        if (renderer != null) {
            synchronized(renderer!!) {
                renderer!!.close()
                renderer = null
            }
        }
        descriptor.close()
        pageSizes = null
    }

    private fun getPageHeight(page: Int): Int {
        return if (pageSizes == null) 0 else (pageSizes!![page].height * scale).toInt()
    }

    fun getPageCount(): Int {
        return try {
            if (renderer == null) 0 else renderer!!.pageCount
        } catch (e: IllegalStateException) {
            // this may happen if the Renderer already got closed
            0
        }
    }

    fun calculatePageNoAtScrollPos(yPos: Int): Int {
        var pageNo = -1
        var y = 0
        while (y <= yPos) {
            y += getPageHeight(pageNo + 1)
            ++pageNo
        }
        return pageNo
    }

    fun calculateScrollPosForPageNo(pageNo: Int): Int {
        var y = 0
        for (page in 0 until pageNo) {
            y += getPageHeight(page)
        }
        return y
    }
}
