package com.example.pdfreader

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation.

class MainActivity : AppCompatActivity() {
    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948

    // manage the pages of the PDF
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null
    var totalPage = 0

    // custom ImageView class that captures strokes and draws them over the image
    lateinit var pageImage: PDFimage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val layout = findViewById<LinearLayout>(R.id.pdfLayout)
        layout.isEnabled = true

        pageImage = PDFimage(this)
        layout.addView(pageImage)
        pageImage.minimumWidth = 1000
        pageImage.minimumHeight = 2000

        val pdfViewModel = ViewModelProvider(this)[PDFViewModel::class.java]

        // open page 0 of the PDF
        try {
            openRenderer(this)
            pdfViewModel.pagenum.observe(this) { pageNumber ->
                showPage(pageNumber)
            }
        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }

        findViewById<TextView>(R.id.filename).apply {
            text = FILENAME
        }
        findViewById<Button>(R.id.undoButton).apply {
            setOnClickListener {
                pageImage.undo()
            }
        }
        findViewById<Button>(R.id.redoButton).apply {
            setOnClickListener {
                pageImage.redo()
            }
        }
        findViewById<TextView>(R.id.textView).also { view ->
            pdfViewModel.pagenum.observe(this) { pagenum ->
                view.text = "Page ${pagenum+1} / ${totalPage}"
            }
        }
        findViewById<Button>(R.id.prevButton).apply {
            setOnClickListener {
                if (currentPage != null && currentPage!!.index > 0) {
                    pdfViewModel.prevpage()
                }
            }
        }
        findViewById<Button>(R.id.nextButton).apply {
            setOnClickListener {
                if (currentPage != null && currentPage!!.index < totalPage - 1) {
                    pdfViewModel.nextpage()
                }
            }
        }
        findViewById<Button>(R.id.penButton).apply {
            setOnClickListener {
                pageImage.setBrush(1)
            }
        }
        findViewById<Button>(R.id.highlightButton).apply {
            setOnClickListener {
                pageImage.setBrush(2)
            }
        }
        findViewById<Button>(R.id.eraserButton).apply {
            setOnClickListener {
                pageImage.setBrush(3)
            }
        }

    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        currentPage?.let { showPage(it.index) }
    }

    override fun onStop() {
        super.onStop()
        Log.d(LOGNAME, "stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOGNAME, "destroyed")
        try {
            closeRenderer()
        } catch (ex: IOException) {
            Log.d(LOGNAME, "Unable to close PDF renderer")
        }
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        // capture PDF data
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
        totalPage = pdfRenderer.pageCount
        pageImage.initdocpaths(totalPage)
    }

    @Throws(IOException::class)
    private fun closeRenderer() {
        currentPage?.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }

    private fun showPage(index: Int) {
        if (pdfRenderer.pageCount <= index) {
            return
        }
        currentPage?.close()

        currentPage = pdfRenderer.openPage(index)

        if (currentPage != null) {
            val origWidth = currentPage!!.width
            val origHeight = currentPage!!.height
            var rotateAngle = 0f
            if (origWidth > origHeight) {
                rotateAngle = 90f
            } else {
                rotateAngle = 0f
            }
            pageImage.rotateAngle = rotateAngle

            val screenWidth = resources.displayMetrics.widthPixels
            val itemRatio = origWidth.toFloat() / origHeight.toFloat()

            val targetWidth = screenWidth
            val targetHeight = (screenWidth / itemRatio).toInt()

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            pageImage.setImage(bitmap)
            pageImage.setPagenum(index)
        }
    }
}