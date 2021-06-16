package com.pdfview

import android.content.Context
import android.util.AttributeSet
import com.pdfview.subsamplincscaleimageview.ImageSource
import com.pdfview.subsamplincscaleimageview.SubsamplingScaleImageView
import java.io.File
import java.util.*

class PDFView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SubsamplingScaleImageView(context, attrs) {

    private var mfile: File? = null
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

    fun fromFile(file: File): PDFView {
        mfile = file
        return this
    }

    fun fromFile(filePath: String): PDFView {
        mfile = File(filePath)
        return this
    }

    fun scale(scale: Float): PDFView {
        mScale = scale
        return this
    }

    fun show() {
        val source = ImageSource.uri(mfile!!.path)
        pdfRegionDecoder = PDFRegionDecoder(view = this, file = mfile!!, scale = mScale)
        setRegionDecoderFactory { pdfRegionDecoder!! }
        setImage(source)
    }

    fun getPageCount():Int {
        return if (pdfRegionDecoder == null) 0 else pdfRegionDecoder!!.getPageCount()
    }

    fun scrollToPage(pageNo: Int) {
        Objects.requireNonNull(pdfRegionDecoder, "PDFRegionDecoder not initialized")

        val scrollPos = (pageNo - 1) * pdfRegionDecoder!!.getPageHeight() * scale
        vTranslate.y = -scrollPos
        refreshRequiredTiles(true)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        this.recycle()
    }
}