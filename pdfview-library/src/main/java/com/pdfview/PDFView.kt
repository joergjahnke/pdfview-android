package com.pdfview

import android.content.Context
import android.graphics.pdf.LoadParams
import android.util.AttributeSet
import com.pdfview.subsamplincscaleimageview.ImageSource
import com.pdfview.subsamplincscaleimageview.SubsamplingScaleImageView
import java.io.File
import java.util.*

class PDFView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SubsamplingScaleImageView(context, attrs) {

    private var mfile: File? = null
    private var mLoadParams: LoadParams? = null
    private var mScale: Float = 8f
    private var pdfRegionDecoder: PDFRegionDecoder? = null

    init {
        setMinimumTileDpi(120)
        setMinimumScaleType(SCALE_TYPE_START)
    }

    fun fromAsset(assetFileName: String): PDFView {
        mfile = FileUtils.fileFromAsset(context, assetFileName)
        return this
    }

    fun fromAsset(assetFileName: String, loadParams: LoadParams): PDFView {
        mfile = FileUtils.fileFromAsset(context, assetFileName)
        mLoadParams = loadParams
        return this
    }

    fun fromFile(file: File): PDFView {
        mfile = file
        return this
    }

    fun fromFile(filePath: String): PDFView {
        mfile = File(filePath)
        return this
    }

    fun fromFile(file: File, loadParams: LoadParams): PDFView {
        mfile = file
        mLoadParams = loadParams
        return this
    }

    fun scale(scale: Float): PDFView {
        mScale = scale
        return this
    }

    fun show() {
        val source = ImageSource.uri(mfile!!.path)
        pdfRegionDecoder = PDFRegionDecoder(view = this, file = mfile!!, scale = mScale, loadParams = mLoadParams)
        setRegionDecoderFactory { pdfRegionDecoder!! }
        setImage(source)
    }

    fun getPageCount():Int {
        return if (pdfRegionDecoder == null) 0 else pdfRegionDecoder!!.getPageCount()
    }

    fun getCurrentPageNo():Int {
        Objects.requireNonNull(pdfRegionDecoder, "PDFRegionDecoder not initialized")

        val scrollPos = -vTranslate.y
        return pdfRegionDecoder!!.calculatePageNoAtScrollPos((scrollPos / scale).toInt()) + 1
    }

    fun scrollToPage(pageNo: Int) {
        Objects.requireNonNull(pdfRegionDecoder, "PDFRegionDecoder not initialized")

        val scrollPos = pdfRegionDecoder!!.calculateScrollPosForPageNo(pageNo - 1) * scale
        vTranslate.y = -scrollPos
        refreshRequiredTiles(true)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        this.recycle()
    }
}