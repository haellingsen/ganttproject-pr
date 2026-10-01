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

import biz.ganttproject.core.option.GPOptionGroup
import biz.ganttproject.core.option.ObservableBooleanOption
import biz.ganttproject.core.option.ObservableEnum
import biz.ganttproject.core.option.ObservableEnumerationOption
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import net.sourceforge.ganttproject.GPLogger
import net.sourceforge.ganttproject.export.*
import net.sourceforge.ganttproject.language.GanttLanguage
import net.sourceforge.ganttproject.task.Task
import org.eclipse.core.runtime.Status
import java.awt.Component
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

enum class OverviewFileFormat(val extension: String) { PDF("pdf"), SVG("svg") }

/**
 * Exports a simple one-page overview of the Gantt chart, see [OverviewLayout].
 * PDF is the default: it opens everywhere and prints on one A4 landscape page.
 * SVG is meant for slides and documents, where it stays sharp at any size.
 */
class ExporterToOverview : ExporterBase() {
  private val format = ObservableEnum("impex.overview.fileformat", OverviewFileFormat.PDF, OverviewFileFormat.entries)
  private val formatOption = ObservableEnumerationOption(format)
  private val detail = ObservableEnum("impex.overview.detail", OverviewDetail.PHASES, OverviewDetail.entries)
  private val detailOption = ObservableEnumerationOption(detail)
  private val progressOption = ObservableBooleanOption("impex.overview.progress", true)
  private val criticalOption = ObservableBooleanOption("impex.overview.critical", false)
  private val optionGroup = GPOptionGroup("impex.overview", formatOption, detailOption, progressOption, criticalOption).also {
    it.isTitled = false
  }

  override val fileTypeDescription: String get() = text("impex.overview.description", "Overview chart")
  override val options: GPOptionGroup get() = optionGroup
  override val secondaryOptions: MutableList<GPOptionGroup>? get() = null
  override val customOptionsUI: Component? get() = null
  override val fileNamePattern: String get() = proposeFileExtension()
  override val fileExtensions: Array<String> get() = OverviewFileFormat.entries.map { it.extension }.toTypedArray()
  override fun proposeFileExtension() = format.value.extension

  /** The overview is the report most people need, so it comes first in the export wizard. */
  override val chooserOrder: Int get() = -1

  override fun setFormat(format: String) {
    OverviewFileFormat.entries.find { it.extension.equals(format, ignoreCase = true) }?.let { this.format.set(it) }
  }

  /** Example image for the selected detail level, so that the user can pick the right kind of report. */
  override fun createPreviewFx(): Node {
    // The examples have different heights. A fixed box keeps the wizard from resizing (and pushing its buttons
    // off the screen) when the user switches between detail levels.
    val image = ImageView().apply {
      isPreserveRatio = true
      isSmooth = true
      fitWidth = PREVIEW_WIDTH
      fitHeight = PREVIEW_HEIGHT
    }
    val imageBox = StackPane(image).apply {
      alignment = Pos.TOP_LEFT
      setMinSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
      setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
      setMaxSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
    }
    val images = mutableMapOf<OverviewDetail, Image?>()
    fun showExample(value: OverviewDetail) {
      image.image = images.getOrPut(value) {
        OverviewLayout::class.java.getResourceAsStream("/export/overview-${value.name.lowercase()}.png")?.use { Image(it) }
      }
    }
    showExample(detail.value)
    detail.addWatcher { showExample(it.newValue) }
    val hint = Label(text("impex.overview.hint",
      "PDF opens everywhere and prints on one A4 landscape page. Choose SVG to insert the chart into PowerPoint or Word (Insert > Pictures).")).apply {
      isWrapText = true
      maxWidth = PREVIEW_WIDTH
      minHeight = Region.USE_PREF_SIZE
      style = "-fx-font-size: 0.9em; -fx-opacity: 0.8;"
    }
    return VBox(6.0, imageBox, hint).apply { padding = Insets(8.0, 0.0, 0.0, 0.0) }
  }

  override fun createJobs(outputFile: File, resultFiles: MutableList<File>): Array<ExporterJob> {
    resultFiles.add(outputFile)
    return arrayOf(JavaExporterJob("Export overview") {
      try {
        val layout = createLayout()
        when (format.value) {
          OverviewFileFormat.PDF -> outputFile.outputStream().buffered().use { layout.draw(PdfOverviewCanvas(it)) }
          OverviewFileFormat.SVG -> outputFile.writeText(SvgOverviewCanvas().also { layout.draw(it) }.result, Charsets.UTF_8)
        }
        Status.OK_STATUS
      } catch (e: Exception) {
        uiFacade.showErrorDialog(e)
        Status.CANCEL_STATUS
      }
    })
  }

  private fun createLayout(): OverviewLayout {
    val taskManager = project.taskManager
    val critical: Set<Task> = if (criticalOption.value) {
      try {
        // Read-only calculation, does not change the critical flags shown in the chart
        taskManager.algorithmCollection.criticalPathAlgorithm.criticalTasks.toSet()
      } catch (e: Exception) {
        GPLogger.log(e)
        emptySet()
      }
    } else emptySet()
    val rows = buildOverviewRows(taskManager, detail.value, critical)
    val settings = OverviewSettings(showProgress = progressOption.value, showCritical = criticalOption.value)
    return OverviewLayout(rows, createLabels(project.projectName, settings.today), settings)
  }

  private fun text(key: String, fallback: String): String = GanttLanguage.getInstance().getText(key) ?: fallback

  companion object {
    private const val PREVIEW_WIDTH = 400.0
    private const val PREVIEW_HEIGHT = 150.0
  }
}

internal fun createLabels(projectName: String?, today: LocalDate): OverviewLabels {
  val language = GanttLanguage.getInstance()
  fun t(key: String, fallback: String) = language.getText(key) ?: fallback
  val locale = language.dateFormatLocale ?: language.locale ?: Locale.getDefault()
  val formatDate = { d: LocalDate -> language.formatShortDate(GregorianCalendar(d.year, d.monthValue - 1, d.dayOfMonth)) }
  return OverviewLabels(
    title = projectName?.takeIf { it.isNotBlank() } ?: t("impex.overview.defaultTitle", "Project overview"),
    statusDate = t("impex.overview.statusDate", "Status as of {0}").replace("{0}", formatDate(today)),
    taskColumn = t("impex.overview.column.task", "Task"),
    datesColumn = t("impex.overview.column.dates", "Dates"),
    completed = t("impex.overview.legend.completed", "Completed"),
    remaining = t("impex.overview.legend.remaining", "Remaining"),
    milestone = t("impex.overview.legend.milestone", "Milestone"),
    milestoneReached = t("impex.overview.legend.milestoneReached", "Milestone reached"),
    critical = t("impex.overview.legend.critical", "Critical path"),
    today = t("impex.overview.legend.today", "Today"),
    noTasks = t("impex.overview.noTasks", "No tasks to show"),
    footer = t("impex.overview.footer", "Created with GanttProject"),
    formatDate = formatDate,
    monthName = { d -> d.month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.') },
    weekLabel = { w -> t("impex.overview.week", "W{0}").replace("{0}", w.toString()) },
    quarterLabel = { q -> t("impex.overview.quarter", "Q{0}").replace("{0}", q.toString()) },
  )
}
