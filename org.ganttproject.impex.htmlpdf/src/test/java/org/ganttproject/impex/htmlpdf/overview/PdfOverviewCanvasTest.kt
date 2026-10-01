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

import com.itextpdf.text.pdf.PdfName
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import net.sourceforge.ganttproject.export.OverviewLabels
import net.sourceforge.ganttproject.export.OverviewLayout
import net.sourceforge.ganttproject.export.OverviewRow
import net.sourceforge.ganttproject.export.OverviewSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class PdfOverviewCanvasTest {
  private val start = LocalDate.of(2026, 8, 3)

  private fun row(i: Int, name: String = "Oppgave $i", milestone: Boolean = false) = OverviewRow(
    name = name, depth = 1, start = start.plusDays(i * 3L), end = start.plusDays(i * 3L + if (milestone) 0 else 5),
    displayEnd = start.plusDays(i * 3L + if (milestone) 0 else 4), isMilestone = milestone, isSummary = false,
    completion = 50, isCritical = false, group = i)

  private fun render(rows: List<OverviewRow>): PdfReader {
    val out = ByteArrayOutputStream()
    OverviewLayout(rows, labels, OverviewSettings(today = start.plusDays(7))).draw(PdfOverviewCanvas(out))
    return PdfReader(out.toByteArray())
  }

  @Test
  fun `fits on one A4 landscape page`() {
    val reader = render(listOf(row(0, "Grunnarbeid på tomta"), row(1), row(2, "Overtakelse", milestone = true)))
    assertEquals(1, reader.numberOfPages)
    val page = reader.getPageSizeWithRotation(1)
    assertEquals(842f, page.width, 1f)
    assertEquals(595f, page.height, 1f)
    val text = PdfTextExtractor.getTextFromPage(reader, 1)
    assertTrue(text.contains("Ny produksjonslinje"), text)
    assertTrue(text.contains("Grunnarbeid på tomta"), "Norwegian letters are kept: $text")
    assertTrue(text.contains("Overtakelse"), text)
    // Colours must be opaque: iText adds a transparency state when a colour has alpha < 255
    val resources = reader.getPageN(1).getAsDict(PdfName.RESOURCES)
    assertNull(resources.getAsDict(PdfName.EXTGSTATE), "nothing is drawn transparent")
  }

  @Test
  fun `long plans get a taller page instead of unreadable text`() {
    val reader = render((0 until 80).map { row(it) })
    assertEquals(1, reader.numberOfPages)
    assertTrue(reader.getPageSizeWithRotation(1).height > 595f)
  }

  private val labels = OverviewLabels(
    title = "Ny produksjonslinje", statusDate = "Status", taskColumn = "Oppgave", datesColumn = "Periode",
    completed = "Fullført", remaining = "Gjenstår", milestone = "Milepæl", milestoneReached = "Milepæl nådd",
    critical = "Kritisk linje", today = "I dag", noTasks = "Ingen oppgaver", footer = "Laget med GanttProject",
    formatDate = { it.toString() }, monthName = { it.month.name.take(3) },
    weekLabel = { "U$it" }, quarterLabel = { "K$it" },
  )
}
