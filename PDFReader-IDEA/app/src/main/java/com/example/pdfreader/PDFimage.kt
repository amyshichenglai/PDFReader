package com.example.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView

@SuppressLint("AppCompatCustomView")
class PDFimage(context: Context?) : ImageView(context) {
    val LOGNAME = "pdf_image"

    // drawing path
    var path: Path? = null
    val undopaths = mutableListOf<MutableList<Path?>>()
    val undopathstype = mutableListOf<MutableList<Int>>()
    val redopaths = mutableListOf<MutableList<Path?>>()
    val redopathstype = mutableListOf<MutableList<Int>>()
    var docHighlights = mutableListOf<MutableList<Path?>>()
    var docStrokes = mutableListOf<MutableList<Path?>>()

    // image to display
    var bitmap: Bitmap? = null
    var pageindex = 0
    var paint = Paint(Color.BLUE)
    var paintMode = 1
    private val strokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.BLACK
    }
    private val highlightPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 50f
        color = Color.YELLOW
    }

    var x1 = 0f
    var x2 = 0f
    var y1 = 0f
    var y2 = 0f
    var old_x1 = 0f
    var old_y1 = 0f
    var old_x2 = 0f
    var old_y2 = 0f
    var mid_x = -1f
    var mid_y = -1f
    var old_mid_x = -1f
    var old_mid_y = -1f
    var p1_id = 0
    var p1_index = 0
    var p2_id = 0
    var p2_index = 0
    var rotateAngle: Float = 0f
    var currentMatrix = Matrix()
    var inverse = Matrix()
    var prevwidth = 0
    var scale = 1f

    init {
        this.paint = strokePaint
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (prevwidth == 0) {
            prevwidth = measuredWidth
        }
    }

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @SuppressLint("ClickableViewAccessibility")
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var inverted = floatArrayOf()
        when (event.pointerCount) {
            1 -> {
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // invert using the current matrix to account for pan/scale
                // inverts in-place and returns boolean
                inverse = Matrix()
                currentMatrix.invert(inverse)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)
                x1 = inverted[0]
                y1 = inverted[1]
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(LOGNAME, "Action down")
                        path = Path()
                        if (paintMode == 1) {
                            docStrokes[pageindex].add(path)
                            addUndoCommand(path, 1)
                        } else if (paintMode == 2) {
                            docHighlights[pageindex].add(path)
                            addUndoCommand(path, 2)
                        } else {
                            val strokeIterator = docStrokes[pageindex].iterator()
                            val point = PointF(x1/scale, y1/scale)
                            while (strokeIterator.hasNext()) {
                                val strokePath = strokeIterator.next()
                                if (isOnPath(strokePath, point)) {
                                    addUndoCommand(strokePath, -1)
                                    strokeIterator.remove()
                                }
                            }
                            val highlightIterator = docHighlights[pageindex].iterator()
                            while (highlightIterator.hasNext()) {
                                val highlightPath = highlightIterator.next()
                                if (isOnPath(highlightPath, point)) {
                                    addUndoCommand(highlightPath, -2)
                                    highlightIterator.remove()
                                }
                            }
                        }
                        redopaths[pageindex].clear()
                        redopathstype[pageindex].clear()
                        path!!.moveTo(x1 / scale, y1 / scale)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        Log.d(LOGNAME, "Action move")
                        path!!.lineTo(x1 / scale, y1 / scale)
                        if (paintMode == 3) {
                            val strokeIterator = docStrokes[pageindex].iterator()
                            val point = PointF(x1/scale, y1/scale)
                            while (strokeIterator.hasNext()) {
                                val strokePath = strokeIterator.next()
                                if (isOnPath(strokePath, point)) {
                                    addUndoCommand(strokePath, -1)
                                    strokeIterator.remove()
                                }
                            }
                            val highlightIterator = docHighlights[pageindex].iterator()
                            while (highlightIterator.hasNext()) {
                                val highlightPath = highlightIterator.next()
                                if (isOnPath(highlightPath, point)) {
                                    addUndoCommand(highlightPath, -2)
                                    highlightIterator.remove()
                                }
                            }
                        }
                        redopaths[pageindex].clear()
                        redopathstype[pageindex].clear()
                    }

                    MotionEvent.ACTION_UP -> Log.d(LOGNAME, "Action up ${docStrokes[0]}")
                }
            }

            2 -> {
                // point 1
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x1 < 0 || old_y1 < 0) {
                    x1 = inverted.get(0)
                    old_x1 = x1
                    y1 = inverted.get(1)
                    old_y1 = y1
                } else {
                    old_x1 = x1
                    old_y1 = y1
                    x1 = inverted.get(0)
                    y1 = inverted.get(1)
                }

                // point 2
                p2_id = event.getPointerId(1)
                p2_index = event.findPointerIndex(p2_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p2_index), event.getY(p2_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x2 < 0 || old_y2 < 0) {
                    x2 = inverted.get(0)
                    old_x2 = x2
                    y2 = inverted.get(1)
                    old_y2 = y2
                } else {
                    old_x2 = x2
                    old_y2 = y2
                    x2 = inverted.get(0)
                    y2 = inverted.get(1)
                }

                // midpoint
                mid_x = (x1 + x2) / 2
                mid_y = (y1 + y2) / 2
                old_mid_x = (old_x1 + old_x2) / 2
                old_mid_y = (old_y1 + old_y2) / 2

                // distance
                val d_old =
                    Math.sqrt(Math.pow((old_x1 - old_x2).toDouble(), 2.0) + Math.pow((old_y1 - old_y2).toDouble(), 2.0))
                        .toFloat()
                val d = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0))
                    .toFloat()

                // pan and zoom during MOVE event
                if (event.action == MotionEvent.ACTION_MOVE) {
                    // pan == translate of midpoint
                    val dx = mid_x - old_mid_x
                    val dy = mid_y - old_mid_y
                    currentMatrix.preTranslate(dx, dy)

                    // zoom == change of spread between p1 and p2
                    var scale = d / d_old
                    scale = Math.max(0f, scale)
                    currentMatrix.preScale(scale, scale, mid_x, mid_y)

                    // reset on up
                } else if (event.action == MotionEvent.ACTION_UP) {
                    old_x1 = -1f
                    old_y1 = -1f
                    old_x2 = -1f
                    old_y2 = -1f
                    old_mid_x = -1f
                    old_mid_y = -1f
                }
            }

            else -> {
            }
        }


        val touchPoint = floatArrayOf(x1, y1)

        x1 = touchPoint[0]
        y1 = touchPoint[1]
        return true
    }

    // set image as background
    fun setImage(bitmap: Bitmap?) {
        this.bitmap = bitmap
    }

    fun setBrush(brushType: Int) {
        this.paintMode = brushType
    }

    fun setPagenum(pagenum: Int) {
        this.pageindex = pagenum
    }

    fun initdocpaths(totalpages: Int) {
        for (i in 0 until totalpages) {
            docStrokes.add(mutableListOf())
            docHighlights.add(mutableListOf())
            undopaths.add(mutableListOf())
            redopaths.add(mutableListOf())
            undopathstype.add(mutableListOf())
            redopathstype.add(mutableListOf())
        }
    }

    fun isOnPath(path: Path?, cursor: PointF): Boolean {
        val point = FloatArray(2)
        val pathmeasure = PathMeasure(path, false)
        val pathlen = pathmeasure.length.toInt()
        for (i in 0 until pathlen) {
            pathmeasure.getPosTan(i.toFloat(), point, null)
            val xdiff = point[0] - cursor.x
            val ydiff = point[1] - cursor.y
            val dist = Math.sqrt((xdiff*xdiff+ydiff*ydiff).toDouble())
            if (dist < 10f) {
                return true
            }
        }
        return false
    }

    fun addUndoCommand(path: Path?, type: Int) {
        if (undopaths[pageindex].size >= 5) {
            undopaths[pageindex].removeAt(0)
            undopathstype[pageindex].removeAt(0)
        }
        undopaths[pageindex].add(path)
        undopathstype[pageindex].add(type)
    }

    fun addRedoCommand(path: Path?, type: Int) {
        redopaths[pageindex].add(path)
        redopathstype[pageindex].add(type)
    }

    fun undo() {
        undopaths[pageindex].removeLastOrNull()?.apply {
            var type = 1
            undopathstype[pageindex].removeLastOrNull()?.apply {
                type = this
            }
            addRedoCommand(this, type)
            if (type == 1) {
                docStrokes[pageindex].removeLastOrNull()
            } else if (type == 2) {
                docHighlights[pageindex].removeLastOrNull()
            } else if (type == -1) {
                docStrokes[pageindex].add(this)
            } else {
                docHighlights[pageindex].add(this)
            }
        }
    }

    fun redo() {
        redopaths[pageindex].removeLastOrNull()?.apply {
            var type = 1
            redopathstype[pageindex].removeLastOrNull()?.apply {
                type = this
            }
            addUndoCommand(this, type)
            if (type == 1) {
                docStrokes[pageindex].add(this)
            } else if (type == 2) {
                docHighlights[pageindex].add(this)
            } else if (type == -1) {
                docStrokes[pageindex].removeLastOrNull()
            } else {
                docHighlights[pageindex].removeLastOrNull()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.concat(currentMatrix)
        // draw background
        if (bitmap != null) {
            setImageBitmap(bitmap)
        }

        scale = width / prevwidth.toFloat()

        // draw lines over it

        for (path in docHighlights[pageindex]) {
            path?.let {
                val scaledPath = scalePath(it, scale)
                canvas.drawPath(scaledPath, highlightPaint)
            }
        }
        for (path in docStrokes[pageindex]) {
            path?.let {
                val scaledPath = scalePath(it, scale)
                canvas.drawPath(scaledPath, strokePaint)
            }
        }
        super.onDraw(canvas)
        getParent().requestDisallowInterceptTouchEvent(true)
    }

    private fun scalePath(path: Path, scale: Float): Path {
        val scaledPath = Path()
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        path.transform(matrix, scaledPath)
        return scaledPath
    }
}