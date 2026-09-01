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

import biz.ganttproject.app.RootLocalizer
import biz.ganttproject.app.properties
import biz.ganttproject.core.option.ObservableString
import javafx.scene.Node
import net.sourceforge.ganttproject.IGanttProject

/**
 * The JavaFX counterpart of ProjectSettingsPanel. The page has no options of its own, so the
 * fields are plain properties which are written to the project when the page is committed.
 */
class ProjectBasicOptionPageFx(private val project: IGanttProject) {
  private val name = ObservableString("project.name", project.projectName)
  private val organization = ObservableString("project.organization", project.organization)
  private val webLink = ObservableString("project.webLink", project.webLink)
  private val description = ObservableString("project.description", project.description)

  fun buildNode(): Node = properties {
    text(name) { labelText = RootLocalizer.formatText("name") }
    text(organization) { labelText = RootLocalizer.formatText("organization") }
    text(webLink) { labelText = RootLocalizer.formatText("webLink") }
    text(description) {
      labelText = RootLocalizer.formatText("shortDescription")
      isMultiline = true
    }
  }

  fun commit() {
    project.projectName = name.value ?: ""
    project.organization = organization.value ?: ""
    project.webLink = webLink.value ?: ""
    project.description = description.value ?: ""
  }
}
