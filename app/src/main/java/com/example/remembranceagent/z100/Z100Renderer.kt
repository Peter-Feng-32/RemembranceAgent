package com.example.remembranceagent.z100

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.Typeface
import com.vuzix.ultralite.Layout
import com.vuzix.ultralite.UltraliteSDK
import com.termux.terminal.TerminalEmulator

class Z100Renderer(textSize: Int, typeface: Typeface?, ultraliteSDK: UltraliteSDK) :
    TerminalRenderer(textSize, typeface) {
    var ultraliteSDK: UltraliteSDK
    private val lockFrames = Any()
    private val lockBitmap = Any()
    var numFramesInTransit = 0
    var frameLimit = 2
    var callbackId = 0

    /*
    * Todo: fix potential bug here with missing callbacks.  Do this by adding a time-to-live to the callbacks.
    *
    * */
    internal inner class CallbackHandler(
        terminalEmulator: TerminalEmulator,
        topRow: Int,
        var callbackId: Int
    ) :
        UltraliteSDK.Canvas.CommitCallback {
        var terminalEmulator: TerminalEmulator
        var topRow: Int

        init {
            this.terminalEmulator = terminalEmulator
            this.topRow = topRow
        }

        override fun done() {
            synchronized(lockFrames) { numFramesInTransit-- }
            if (droppedBitmap) {
                droppedBitmap = false
                renderToZ100(terminalEmulator, topRow)
            }
        }
    }

    private var original: Bitmap? = null
    private var droppedBitmap = false

    init {
        this.ultraliteSDK = ultraliteSDK
        ultraliteSDK.requestControl()
        ultraliteSDK.setLayout(Layout.CANVAS, 0, true)
        ultraliteSDK.getCanvas().clearBackground()
        ultraliteSDK.getCanvas().commit()
    }

    fun renderToZ100(terminalEmulator: TerminalEmulator, topRow: Int) {
        val bitmap = Bitmap.createBitmap(
            UltraliteSDK.Canvas.WIDTH,
            UltraliteSDK.Canvas.HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        this.render(terminalEmulator, canvas, topRow, -1, -1, -1, -1)
        var boundingBox: Rect
        synchronized(lockBitmap) {
            boundingBox = findBoundingBoxOfDifferences(original, bitmap)
            if (boundingBox.bottom == boundingBox.top || boundingBox.left == boundingBox.right) {
                return  // No changes
            }
            val glassesBitmap = Bitmap.createBitmap(
                bitmap,
                boundingBox.left,
                boundingBox.top,
                boundingBox.width(),
                boundingBox.height()
            )
            synchronized(lockFrames) {
                if (numFramesInTransit == frameLimit) {
                    droppedBitmap = true
                    return
                }
                ultraliteSDK
                    .getCanvas()
                    .drawBackground(glassesBitmap, boundingBox.left, boundingBox.top)
                numFramesInTransit++
                ultraliteSDK
                    .getCanvas()
                    .commit(CallbackHandler(terminalEmulator, topRow, callbackId++))
            }
            original = bitmap
        }
    }

    companion object {
        fun findBoundingBoxOfDifferences(original: Bitmap?, modified: Bitmap): Rect {
            if (original == null) {
                return Rect(0, 0, modified.width - 1, modified.height - 1)
            }
            if (original.width != modified.width || original.height != modified.height) {
                return Rect(0, 0, modified.width - 1, modified.height - 1)
            }
            val width = original.width
            val height = original.height
            var minX = width // Initialize to width to find the smallest x
            var minY = height // Initialize to height to find the smallest y
            var maxX = 0 // Initialize to 0 to find the largest x
            var maxY = 0 // Initialize to 0 to find the largest y
            var changesDetected = false
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (original.getPixel(x, y) != modified.getPixel(x, y)) {
                        changesDetected = true
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            return if (!changesDetected) {
                // No changes detected; return an empty rect or a rect covering the entire bitmap.
                Rect(0, 0, 0, 0)
            } else Rect(minX, minY, maxX, maxY)

            // Return the bounding box with inclusive coordinates.
        }
    }
}