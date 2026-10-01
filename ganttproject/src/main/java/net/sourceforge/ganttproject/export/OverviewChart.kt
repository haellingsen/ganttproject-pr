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
package net.sourceforge.ganttproject.export

import net.sourceforge.ganttproject.task.Task
import net.sourceforge.ganttproject.task.TaskManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.*

/**
 * One-page overview of the schedule, meant for stakeholders rather than for the team.
 *
 * The layout follows commonly cited advice on schedule graphics:
 * - few rows: phases and milestones, not every work package (PMBOK "milestone chart"/summary schedule,
 *   MS Project Timeline view);
 * - labels in a column next to the bars, whole period on one landscape page (think-cell, Tufte);
 * - no grid prison, only faint lines at the coarse time unit (Tufte: "degrid");
 * - one neutral bar colour, progress as darker fill, one accent for the status date and optionally
 *   one for the critical path (Few: data-ink, no decoration);
 * - milestones as diamonds with their date, filled when reached;
 * - dependency arrows are left out, they belong to the detailed plan.
 */
enum class OverviewDetail { MILESTONES, PHASES, ALL }

data class OverviewRow(
  val name: String,
  /** 1 for top-level tasks */
  val depth: Int,
  val start: LocalDate,
  /** Exclusive end, equals [start] for milestones */
  val end: LocalDate,
  /** Inclusive end, as shown to the user */
  val displayEnd: LocalDate,
  val isMilestone: Boolean,
  val isSummary: Boolean,
  val completion: Int,
  val isCritical: Boolean,
  /** Index of the top-level task this row belongs to, used for alternating band shading */
  val group: Int,
)

data class OverviewLabels(
  val title: String,
  val statusDate: String,
  val taskColumn: String,
  val datesColumn: String,
  val completed: String,
  val remaining: String,
  val milestone: String,
  val milestoneReached: String,
  val critical: String,
  val today: String,
  val noTasks: String,
  val footer: String,
  val formatDate: (LocalDate) -> String,
  val monthName: (LocalDate) -> String,
  val weekLabel: (Int) -> String,
  val quarterLabel: (Int) -> String,
)

data class OverviewSettings(
  val showProgress: Boolean = true,
  val showCritical: Boolean = false,
  val today: LocalDate = LocalDate.now(),
)

// Read the fields of the calendar itself: GanttProject sets the default time zone to UTC, so going through
// Date and ZoneId.systemDefault() may shift the date by one day.
private fun toLocalDate(c: Calendar): LocalDate =
  LocalDate.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

/**
 * Collects the rows to show, in document order.
 */
fun buildOverviewRows(taskManager: TaskManager, detail: OverviewDetail, criticalTasks: Set<Task> = emptySet()): List<OverviewRow> {
  val hierarchy = taskManager.taskHierarchy
  val root = taskManager.rootTask
  var group = -1
  val result = mutableListOf<OverviewRow>()
  for (task in hierarchy.tasksInDocumentOrder) {
    if (task == root) continue
    var depth = 0
    var t: Task? = task
    while (t != null && t != root) {
      depth++
      t = t.supertask
    }
    if (depth == 1) group++
    val include = when (detail) {
      OverviewDetail.ALL -> true
      OverviewDetail.PHASES -> depth <= 2 || task.isMilestone
      OverviewDetail.MILESTONES -> task.isMilestone
    }
    if (!include) continue
    val start = toLocalDate(task.start)
    val end = if (task.isMilestone) start else toLocalDate(task.end)
    val displayEnd = if (task.isMilestone) start else toLocalDate(task.displayEnd)
    result.add(OverviewRow(
      name = task.name ?: "",
      // Without the parent rows, indentation and group bands would only add noise
      depth = if (detail == OverviewDetail.MILESTONES) 1 else depth,
      start = start,
      end = if (end.isAfter(start)) end else if (task.isMilestone) start else start.plusDays(1),
      displayEnd = if (displayEnd.isBefore(start)) start else displayEnd,
      isMilestone = task.isMilestone,
      isSummary = hierarchy.hasNestedTasks(task),
      completion = task.completionPercentage.coerceIn(0, 100),
      isCritical = criticalTasks.contains(task),
      group = if (detail == OverviewDetail.MILESTONES) 0 else maxOf(group, 0),
    ))
  }
  return result
}

internal enum class ScaleUnit { DAY, WEEK, MONTH, QUARTER, YEAR }

internal fun ScaleUnit.floor(date: LocalDate): LocalDate = when (this) {
  ScaleUnit.DAY -> date
  ScaleUnit.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  ScaleUnit.MONTH -> date.withDayOfMonth(1)
  ScaleUnit.QUARTER -> date.withDayOfMonth(1).withMonth(((date.monthValue - 1) / 3) * 3 + 1)
  ScaleUnit.YEAR -> date.withDayOfYear(1)
}

internal fun ScaleUnit.next(date: LocalDate): LocalDate = when (this) {
  ScaleUnit.DAY -> date.plusDays(1)
  ScaleUnit.WEEK -> date.plusWeeks(1)
  ScaleUnit.MONTH -> date.plusMonths(1)
  ScaleUnit.QUARTER -> date.plusMonths(3)
  ScaleUnit.YEAR -> date.plusYears(1)
}

/** Picks a two-tier scale so that the minor unit gives readable cells over the whole period. */
internal fun chooseScale(start: LocalDate, end: LocalDate, chartWidth: Double): Pair<ScaleUnit, ScaleUnit> {
  val pxPerDay = chartWidth / maxOf(ChronoUnit.DAYS.between(start, end), 1)
  return when {
    pxPerDay >= 14.0 -> ScaleUnit.DAY to ScaleUnit.MONTH
    pxPerDay * 7 >= 18.0 -> ScaleUnit.WEEK to ScaleUnit.MONTH
    pxPerDay * 30 >= 22.0 -> ScaleUnit.MONTH to ScaleUnit.YEAR
    else -> ScaleUnit.QUARTER to ScaleUnit.YEAR
  }
}

internal object Palette {
  const val text = "#1f2937"
  const val textMuted = "#6b7280"
  const val band = "#f3f5f8"
  const val gridMajor = "#dde2e9"
  const val tick = "#c3cad4"
  const val bar = "#c9d6e8"
  const val barDone = "#4a6fa5"
  const val critical = "#f2c4bd"
  const val criticalDone = "#c0392b"
  const val milestone = "#2f3b52"
  const val today = "#e07a1f"
  const val white = "#ffffff"
}

enum class TextAnchor { START, MIDDLE, END }

/**
 * Drawing surface for [OverviewLayout]. Coordinates are CSS pixels (96 per inch) with the origin in the top left
 * corner and the y axis pointing down. Text is positioned by its baseline.
 */
interface OverviewCanvas {
  fun begin(width: Double, height: Double, title: String)
  fun text(x: Double, y: Double, s: String, size: Double, bold: Boolean, color: String, anchor: TextAnchor)
  fun rect(x: Double, y: Double, w: Double, h: Double, fill: String, radius: Double)
  fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: String, width: Double)
  fun diamond(cx: Double, cy: Double, r: Double, fill: String, stroke: String)
  fun end()
}

/**
 * Lays out the overview on a page 1123 px wide (A4 landscape at 96 dpi). The height grows with the number of rows.
 * The same layout is drawn into SVG and PDF, so both formats look the same.
 */
class OverviewLayout(
  private val rows: List<OverviewRow>,
  private val labels: OverviewLabels,
  private val settings: OverviewSettings,
) {
  private val pageWidth = 1123.0
  private val margin = 32.0
  private val rowHeight = 22.0
  private val tierHeight = 18.0
  private val headerTop = 92.0
  private lateinit var canvas: OverviewCanvas

  fun draw(canvas: OverviewCanvas) {
    this.canvas = canvas
    val nameWidth = (rows.maxOfOrNull { indent(it) + it.name.length * charWidth(it) + 12.0 } ?: 0.0).coerceIn(180.0, 340.0)
    val datesWidth = 128.0
    val chartLeft = margin + nameWidth + datesWidth + 12.0
    val chartRight = pageWidth - margin
    val rowsTop = headerTop + 2 * tierHeight + 6.0
    val rowsBottom = rowsTop + maxOf(rows.size, 1) * rowHeight
    canvas.begin(pageWidth, rowsBottom + 74.0, labels.title)

    // Title block
    text(margin, 44.0, labels.title, size = 20, weight = 600)
    text(chartRight, 44.0, labels.statusDate, size = 12, color = Palette.textMuted, anchor = "end")
    if (rows.isNotEmpty()) {
      val first = rows.minOf { it.start }
      val last = rows.maxOf { it.displayEnd }
      text(margin, 66.0, "${labels.formatDate(first)} – ${labels.formatDate(last)}", size = 12, color = Palette.textMuted)
    }

    // Column headings, aligned with the lower scale tier
    val headingY = headerTop + 2 * tierHeight - 5.0
    text(margin, headingY, labels.taskColumn, size = 11, weight = 600, color = Palette.textMuted)
    text(margin + nameWidth, headingY, labels.datesColumn, size = 11, weight = 600, color = Palette.textMuted)

    if (rows.isEmpty()) {
      text(margin, rowsTop + 15.0, labels.noTasks, size = 12, color = Palette.textMuted)
      canvas.end()
      return
    }

    val periodStart = rows.minOf { it.start }
    val periodEnd = rows.maxOf { it.end }.let { if (it.isAfter(periodStart)) it else periodStart.plusDays(1) }
    val (minor, major) = chooseScale(periodStart, periodEnd, chartRight - chartLeft)
    val rangeStart = minor.floor(periodStart)
    val rangeEnd = minor.floor(periodEnd).let { if (it == periodEnd) it else minor.next(it) }
    val totalDays = ChronoUnit.DAYS.between(rangeStart, rangeEnd).toDouble()
    val x = { d: LocalDate -> chartLeft + ChronoUnit.DAYS.between(rangeStart, d) / totalDays * (chartRight - chartLeft) }

    // Alternating bands per top-level group (think-cell style row grouping instead of grid lines)
    rows.forEachIndexed { i, row ->
      if (row.group % 2 == 1) {
        rect(margin - 6.0, rowsTop + i * rowHeight, chartRight - margin + 12.0, rowHeight, Palette.band)
      }
    }

    // Time scale: major tier with labels and faint full-height lines, minor tier with ticks only
    val majorY = headerTop
    val minorY = headerTop + tierHeight
    var m = major.floor(rangeStart)
    while (m.isBefore(rangeEnd)) {
      val next = major.next(m)
      val x0 = x(maxOf(m, rangeStart))
      val x1 = x(minOf(next, rangeEnd))
      if (!m.isBefore(rangeStart)) {
        line(x0, majorY, x0, rowsBottom, Palette.gridMajor)
      }
      val label = if (major == ScaleUnit.YEAR) m.year.toString() else "${labels.monthName(m)} ${m.year}"
      if (x1 - x0 >= label.length * 6.0) {
        text(x0 + 4.0, majorY + 13.0, label, size = 11, weight = 600, color = Palette.text)
      }
      m = next
    }
    line(chartRight, majorY, chartRight, rowsBottom, Palette.gridMajor)
    var c = rangeStart
    while (c.isBefore(rangeEnd)) {
      val next = minor.next(c)
      val x0 = x(c)
      val width = x(next) - x0
      line(x0, minorY + 4.0, x0, minorY + tierHeight, Palette.tick)
      val label = when (minor) {
        ScaleUnit.DAY -> c.dayOfMonth.toString()
        ScaleUnit.WEEK -> labels.weekLabel(c.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        ScaleUnit.MONTH -> labels.monthName(c).take(3)
        ScaleUnit.QUARTER -> labels.quarterLabel((c.monthValue - 1) / 3 + 1)
        ScaleUnit.YEAR -> c.year.toString()
      }
      if (width >= label.length * 5.0 + 2) {
        text(x0 + width / 2, minorY + 13.0, label, size = 9, color = Palette.textMuted, anchor = "middle")
      }
      c = next
    }
    line(chartLeft, rowsTop - 2.0, chartRight, rowsTop - 2.0, Palette.tick)

    // Rows
    rows.forEachIndexed { i, row ->
      val top = rowsTop + i * rowHeight
      val mid = top + rowHeight / 2
      val name = fit(row.name, nameWidth - indent(row) - 12.0, charWidth(row))
      text(margin + indent(row), mid + 4.0, name, size = 12, weight = if (row.isSummary) 600 else 400)
      val dates = if (row.isMilestone) labels.formatDate(row.start)
        else "${labels.formatDate(row.start)} – ${labels.formatDate(row.displayEnd)}"
      text(margin + nameWidth, mid + 4.0, dates, size = 11, color = Palette.textMuted)

      val critical = settings.showCritical && row.isCritical
      if (row.isMilestone) {
        val cx = x(row.start)
        val reached = row.completion >= 100
        val stroke = if (critical) Palette.criticalDone else Palette.milestone
        diamond(cx, mid, 6.5, if (reached) stroke else Palette.white, stroke)
        val label = labels.formatDate(row.start)
        if (cx + 12.0 + label.length * 5.6 <= chartRight) {
          text(cx + 11.0, mid + 3.5, label, size = 10, color = Palette.textMuted)
        } else {
          text(cx - 11.0, mid + 3.5, label, size = 10, color = Palette.textMuted, anchor = "end")
        }
      } else {
        val x0 = x(row.start)
        val w = maxOf(x(row.end) - x0, 2.0)
        val h = if (row.isSummary) 8.0 else 12.0
        val (base, done) = when {
          critical -> Palette.critical to Palette.criticalDone
          else -> Palette.bar to Palette.barDone
        }
        rect(x0, mid - h / 2, w, h, base, rx = 2.0)
        if (settings.showProgress && row.completion > 0) {
          rect(x0, mid - h / 2, w * row.completion / 100.0, h, done, rx = 2.0)
        }
      }
    }

    // Status date line, drawn last so it stays visible on top of the bars
    if (!settings.today.isBefore(rangeStart) && settings.today.isBefore(rangeEnd)) {
      val tx = x(settings.today)
      canvas.line(tx, rowsTop - 2.0, tx, rowsBottom + 4.0, Palette.today, 1.5)
      text(tx, rowsBottom + 16.0, "${labels.today} ${labels.formatDate(settings.today)}", size = 10, weight = 600,
        color = Palette.today, anchor = "middle")
    }

    // Legend: only for encodings which are not self-explanatory
    var lx = margin
    val ly = rowsBottom + 44.0
    if (settings.showProgress && rows.any { !it.isMilestone }) {
      rect(lx, ly - 9.0, 18.0, 10.0, Palette.barDone, rx = 2.0)
      lx = legendText(lx + 24.0, ly, labels.completed)
      rect(lx, ly - 9.0, 18.0, 10.0, Palette.bar, rx = 2.0)
      lx = legendText(lx + 24.0, ly, labels.remaining)
    }
    if (rows.any { it.isMilestone }) {
      diamond(lx + 6.0, ly - 4.0, 5.5, Palette.milestone, Palette.milestone)
      lx = legendText(lx + 16.0, ly, labels.milestoneReached)
      diamond(lx + 6.0, ly - 4.0, 5.5, Palette.white, Palette.milestone)
      lx = legendText(lx + 16.0, ly, labels.milestone)
    }
    if (settings.showCritical && rows.any { it.isCritical }) {
      rect(lx, ly - 9.0, 18.0, 10.0, Palette.criticalDone, rx = 2.0)
      lx = legendText(lx + 24.0, ly, labels.critical)
    }
    text(chartRight, ly, labels.footer, size = 10, color = Palette.textMuted, anchor = "end")

    canvas.end()
  }

  private fun charWidth(row: OverviewRow) = if (row.isSummary) 6.9 else 6.4

  private fun indent(row: OverviewRow) = (minOf(row.depth, 4) - 1) * 14.0

  private fun legendText(x: Double, y: Double, s: String): Double {
    text(x, y, s, size = 10, color = Palette.textMuted)
    return x + s.length * 5.6 + 18.0
  }

  private fun fit(s: String, width: Double, charWidth: Double): String {
    val max = (width / charWidth).toInt()
    return if (s.length <= max || max < 2) s else s.take(max - 1).trimEnd() + "…"
  }

  private fun text(x: Double, y: Double, s: String, size: Int, weight: Int = 400, color: String = Palette.text, anchor: String = "start") {
    canvas.text(x, y, s, size.toDouble(), weight >= 600, color, when (anchor) {
      "middle" -> TextAnchor.MIDDLE
      "end" -> TextAnchor.END
      else -> TextAnchor.START
    })
  }

  private fun rect(x: Double, y: Double, w: Double, h: Double, fill: String, rx: Double = 0.0) = canvas.rect(x, y, w, h, fill, rx)

  private fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: String) = canvas.line(x1, y1, x2, y2, stroke, 1.0)

  private fun diamond(cx: Double, cy: Double, r: Double, fill: String, stroke: String) = canvas.diamond(cx, cy, r, fill, stroke)
}

/** Writes the overview as a standalone SVG document. */
class SvgOverviewCanvas : OverviewCanvas {
  private val out = StringBuilder()
  private val fontFamily = "Segoe UI, Helvetica, Arial, sans-serif"

  val result: String get() = out.toString()

  override fun begin(width: Double, height: Double, title: String) {
    out.setLength(0)
    out.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
    out.append("""<svg xmlns="http://www.w3.org/2000/svg" width="${n(width)}" height="${n(height)}" viewBox="0 0 ${n(width)} ${n(height)}" font-family="${esc(fontFamily)}">""").append('\n')
    out.append("""<title>${esc(title)}</title>""").append('\n')
    out.append("""<rect x="0" y="0" width="${n(width)}" height="${n(height)}" fill="${Palette.white}"/>""").append('\n')
  }

  override fun text(x: Double, y: Double, s: String, size: Double, bold: Boolean, color: String, anchor: TextAnchor) {
    out.append("""<text x="${n(x)}" y="${n(y)}" font-size="${n(size)}"""")
    if (bold) out.append(""" font-weight="600"""")
    when (anchor) {
      TextAnchor.MIDDLE -> out.append(""" text-anchor="middle"""")
      TextAnchor.END -> out.append(""" text-anchor="end"""")
      TextAnchor.START -> {}
    }
    out.append(""" fill="$color">${esc(s)}</text>""").append('\n')
  }

  override fun rect(x: Double, y: Double, w: Double, h: Double, fill: String, radius: Double) {
    out.append("""<rect x="${n(x)}" y="${n(y)}" width="${n(w)}" height="${n(h)}"""")
    if (radius > 0) out.append(""" rx="${n(minOf(radius, w / 2))}"""")
    out.append(""" fill="$fill"/>""").append('\n')
  }

  override fun line(x1: Double, y1: Double, x2: Double, y2: Double, stroke: String, width: Double) {
    out.append("""<line x1="${n(x1)}" y1="${n(y1)}" x2="${n(x2)}" y2="${n(y2)}" stroke="$stroke" stroke-width="${n(width)}"/>""").append('\n')
  }

  override fun diamond(cx: Double, cy: Double, r: Double, fill: String, stroke: String) {
    out.append("""<polygon points="${n(cx)},${n(cy - r)} ${n(cx + r)},${n(cy)} ${n(cx)},${n(cy + r)} ${n(cx - r)},${n(cy)}" fill="$fill" stroke="$stroke" stroke-width="1.5"/>""").append('\n')
  }

  override fun end() {
    out.append("</svg>\n")
  }

  private fun n(v: Double) = String.format(Locale.ROOT, "%.1f", v)

  private fun esc(s: String) = buildString {
    for (ch in s) when (ch) {
      '&' -> append("&amp;")
      '<' -> append("&lt;")
      '>' -> append("&gt;")
      '"' -> append("&quot;")
      else -> if (ch < ' ' && ch != '\t') append(' ') else append(ch)
    }
  }
}

/** Renders the overview as a standalone SVG document. */
class OverviewSvgRenderer(
  private val rows: List<OverviewRow>,
  private val labels: OverviewLabels,
  private val settings: OverviewSettings,
) {
  fun render(): String = SvgOverviewCanvas().also { OverviewLayout(rows, labels, settings).draw(it) }.result
}
