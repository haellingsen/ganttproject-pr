/*
 * Copyright 2026 BarD Software s.r.o., Dmitry Barashev.
 *
 * This file is part of GanttProject, an opensource project management tool.
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
package net.sourceforge.ganttproject.gui.options

import biz.ganttproject.app.Localizer
import biz.ganttproject.app.RootLocalizer
import biz.ganttproject.app.i18n
import biz.ganttproject.app.properties
import biz.ganttproject.core.option.GPOptionGroup
import javafx.scene.Node
import javafx.scene.layout.VBox

/**
 * Builds a JavaFX page from the option groups of a settings page which has no custom Swing
 * component. The pages built this way implement FxUiComponent and are shown in the settings dialog
 * directly, without the SwingNode wrapper and its asynchronous content installation.
 *
 * The options render themselves through GPOption.visitPropertyPaneBuilder. An option type which
 * does not implement it yet shows up as "--- MISSING: <option id> ---" in the page.
 */
fun buildFxOptionPage(groups: Array<GPOptionGroup>): Node = VBox().also { box ->
  groups.forEach { group ->
    box.children.add(properties(group.localizer()) {
      if (group.isTitled) {
        title(RootLocalizer.create("optionGroup.${group.id}.label"))
      }
      group.options.filter { it.hasUi() }.forEach { option ->
        option.visitPropertyPaneBuilder(this)
      }
    })
  }
}

/**
 * Resolves the texts of a group the way the Swing page builder does. A group may map the canonical
 * key of an option to a key of its own, and that mapping wins. Otherwise the label is looked up as
 * option.<group id>.<option id>.label and then as option.<option id>.label. A key which is not a
 * label at all is the value of an enumeration option, and is looked up as an option value.
 */
private fun GPOptionGroup.localizer(): Localizer {
  val customKeys = options.mapNotNull { option ->
    getI18Nkey("option.${option.id}.label")?.let { "${option.id}.label" to it }
  }.toMap()
  return i18n {
    default()
    map(customKeys) {
      default()
      prefix("option.$id") {
        fallback {
          default()
          prefix("option") {
            fallback {
              default()
              transform { "optionValue.$it.label" }
            }
          }
        }
      }
    }
  }
}
