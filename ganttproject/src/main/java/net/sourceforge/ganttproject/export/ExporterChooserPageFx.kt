/*
 * Copyright (c) 2026 Dmitry Barashev, BarD Software s.r.o.
 *
 * This file is part of GanttProject, an open-source project management tool.
 *
 * GanttProject is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * GanttProject is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sourceforge.ganttproject.export

import biz.ganttproject.app.*
import javafx.scene.Node
import javafx.geometry.Pos
import javafx.scene.control.ScrollPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.Screen
import biz.ganttproject.app.WizardPage
import java.awt.Component

/**
 * This is the first page of the export wizard that allows the user to choose an exporter.
 */
class ExporterChooserPageFx(exporters: List<Exporter>, private val model: ExportWizardModel) : WizardPage {
  override val title: String = i18n.formatText("option.exporter.title")
  override val component: Component? = null

  private val titles = exporters.flatMapIndexed { index, exporter -> listOf(
    "title.$index" to { LocalizedString(exporter.fileTypeDescription, DummyLocalizer)},
    "title.$index.help" to { LocalizedString("", DummyLocalizer)}
  )}.toMap()

  private val optionPaneBuilder = OptionPaneBuilder<Exporter>().apply {
    this.i18n = MappingLocalizer(titles, DummyLocalizer::create)
    this.styleClass = "exporter-chooser-page"
    elements = exporters.mapIndexed { index, exporter ->
      OptionElementData("title.${index}", exporter, isSelected = (exporter == (model.exporter ?: exporters.first())),
        customContent = buildCustomContent(exporter))
    }
    onSelect = { model.exporter = it }
  }
  override val fxComponent: Node? by lazy {
    // The list of exporters with their options and previews can be taller than a small screen.
    // Scroll it, so that the wizard buttons always stay visible.
    ScrollPane(optionPaneBuilder.buildPane()).apply {
      isFitToWidth = true
      hbarPolicy = ScrollPane.ScrollBarPolicy.NEVER
      maxHeight = Screen.getPrimary().visualBounds.height * MAX_SCREEN_SHARE
      style = "-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;"
      StackPane.setAlignment(this, Pos.TOP_CENTER)
    }
  }

  private fun buildCustomContent(exporter: Exporter): Node? {
    val optionsPane = if (exporter.options.options.isEmpty()) null
    else
    properties(propertyLocalizer) {
      exporter.options.options.forEach {
        it.visitPropertyPaneBuilder(this)
      }
    }
    val preview = exporter.createPreviewFx() ?: return optionsPane
    return if (optionsPane == null) preview else VBox(optionsPane, preview)
  }

  override fun setActive(b: Boolean) {
    if (!b) {
      model.exporter?.options?.commit()
    } else {
      optionPaneBuilder.selectedElement?.userData?.let { model.exporter = it}
    }
  }
}

private const val MAX_SCREEN_SHARE = 0.6

private val propertyLocalizer = i18n {
  default()
  prefix("option") {
    fallback {
      default()
      transform {
        val replaced =
          if (it.contains(".format.value")) it.replace(".format.value", ".fileformat")
          else if (it.contains("fileformat.value")) it.replace(".value", "")
          else it
        "optionValue.$replaced.label"
      }
      //debug("exporter.dropdown.value")
    }
  }
}
private val i18n = RootLocalizer