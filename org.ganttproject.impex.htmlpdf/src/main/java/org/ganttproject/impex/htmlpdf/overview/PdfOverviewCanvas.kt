/*
 * GanttProject is an opensource project management tool. License: GPL3
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package org.ganttproject.impex.htmlpdf.overview

import com.itextpdf.text.BaseColor
import com.itextpdf.text.Document
import com.itextpdf.text.PageSize
import com.itextpdf.text.Rectangle
import com.itextpdf.text.pdf.BaseFont
import com.itextpdf.text.pdf.PdfContentByte
import com.itextpdf.text.pdf.PdfWriter
import net.sourceforge.ganttproject.GPLogger
import net.sourceforge.ganttproject.export.OverviewCanvas
import net.sourceforge.ganttproject.export.OverviewLayout
import net.sourceforge.ganttproject.export.TextAnchor
import java.io.OutputStream

/**
 * Draws the overview into a one-page PDF. The layout is made for A4 landscape: it is scaled to fit the page,
 * and only a very long list of rows gets a taller page, so that the text stays readable.
 */
class PdfOverviewCanvas(private val output: OutputStream) : OverviewCanvas {
  private lateinit var document: Document
  private lateinit var cb: PdfContentByte
  private var scale = PX_TO_PT
  private var offsetX = 0f
  private var pageHeight = 0f
  private val font: BaseFont by lazy { loadFont() }

  override fun begin(width: Double, height: Double, title: String) {
    val a4 = PageSize.A4.rotate()
    val fitScale = minOf(a4.width / width, a4.height / height).toFloat()
    val page = if (fitScale >= MIN_SCALE) {
      scale = fitScale
      a4
    } else {
      scale = PX_TO_PT
      Rectangle(a4.width, (height * PX_TO_PT).toFloat())
    }
    offsetX = ((page.width - width * scale) / 2).toFloat()
    pageHeight = page.height
    document = Document(page, 0f, 0f, 0f, 0f)
    val writer = PdfWriter.getInstance(document, output)
    document.addTitle(title)
    document.addCreator("GanttProject")
    document.open()
    cb = writer.directContent
  }

  override fun text(x: Double, y: Double, s: String, size: Double, bold: Boolean, color: String, anchor: TextAnchor) {
    if (s.isEmpty()) return
    val fill = color(color)
    cb.saveState()
    cb.beginText()
    cb.setFontAndSize(font, (size * scale).toFloat())
    cb.setColorFill(fill)
    if (bold) {
      // The bundled font has no bold face, so we make the glyphs heavier with a thin outline
      cb.setTextRenderingMode(PdfContentByte.TEXT_RENDER_MODE_FILL_STROKE)
      cb.setColorStroke(fill)
      cb.setLineWidth((size * scale * 0.045).toFloat())
    }
    val alignment = when (anchor) {
      TextAnchor.START -> PdfContentByte.ALIGN_LEFT
      TextAnchor.MIDDLE -> PdfContentByte.ALIGN_CENTER
      TextAnchor.END -> PdfContentByte.ALIGN_RIGHT
    }
    cb.showTextAligned(alignment, s, px(x), py(y), 0f)
    cb.endText()
    cb.restoreState()
  }

  override fun rect(x: Double, y: Double, w: Double, h: Double, fill: String, radius: Double) {
    cb.saveState()
    cb.setColorFill(color(fill))
    val width = (w * scale).toFloat()
    val height = (h * scale).toFloat()
    val r = minOf(radius * scale, width / 2.0, height / 2.0).toFloat()
    if (r > 0f) cb.roundRectangle(px(x), py(y + h), width, height, r) else cb.rectangle(px(x), py(y + h), width, height)
    cb.fill()
    cb.restoreState()
  }

  override fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: String, width: Double) {
    cb.saveState()
    cb.setColorStroke(color(stroke))
    cb.setLineWidth((width * scale).toFloat())
    cb.moveTo(px(x1), py(y1))
    cb.lineTo(px(x2), py(y2))
    cb.stroke()
    cb.restoreState()
  }

  override fun diamond(cx: Double, cy: Double, r: Double, fill: String, stroke: String) {
    cb.saveState()
    cb.setColorFill(color(fill))
    cb.setColorStroke(color(stroke))
    cb.setLineWidth((1.5 * scale).toFloat())
    cb.moveTo(px(cx), py(cy - r))
    cb.lineTo(px(cx + r), py(cy))
    cb.lineTo(px(cx), py(cy + r))
    cb.lineTo(px(cx - r), py(cy))
    cb.closePath()
    cb.fillStroke()
    cb.restoreState()
  }

  override fun end() {
    document.close()
  }

  private fun px(x: Double) = (offsetX + x * scale).toFloat()

  private fun py(y: Double) = (pageHeight - y * scale).toFloat()

  private fun color(hex: String): BaseColor {
    val rgb = Integer.parseInt(hex.removePrefix("#"), 16)
    // BaseColor(int) expects ARGB, so pass the channels explicitly to get an opaque colour
    return BaseColor((rgb shr 16) and 0xff, (rgb shr 8) and 0xff, rgb and 0xff)
  }

  private fun loadFont(): BaseFont =
    try {
      val bytes = OverviewLayout::class.java.getResourceAsStream(FONT_PATH)?.use { it.readBytes() }
        ?: error("Font $FONT_PATH not found")
      BaseFont.createFont("LiberationSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null)
    } catch (e: Exception) {
      // Helvetica covers Western European languages, including Norwegian letters
      GPLogger.log(e)
      BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
    }

  companion object {
    private const val PX_TO_PT = 0.75f
    /** Below this scale the text becomes too small to read, so a taller page is used instead. */
    private const val MIN_SCALE = 0.5f
    private const val FONT_PATH = "/fonts/LiberationSans-Regular.ttf"
  }
}
