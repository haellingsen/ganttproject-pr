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
package biz.ganttproject.ganttview

import biz.ganttproject.FXUtil
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.input.KeyCode
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.Stage
import net.sourceforge.ganttproject.gui.UIFacade
import net.sourceforge.ganttproject.task.Task
import net.sourceforge.ganttproject.task.TaskSelectionManager
import net.sourceforge.ganttproject.task.dependency.TaskDependency

/**
 * A small window which lists the dependencies of the selected task: the tasks it waits for and the
 * tasks which wait for it. Activating an entry selects that task, so the window is a way of walking
 * the dependency chain without hunting for the lines in the chart.
 */
class DependencyNavigator(private val uiFacade: UIFacade) {
  private data class Link(val task: Task, val isPredecessor: Boolean, val hardness: TaskDependency.Hardness?)

  private val links = FXCollections.observableArrayList<Link>()
  private val listView = ListView(links)
  private val header = Label()
  private var stage: Stage? = null

  init {
    listView.setCellFactory {
      object : ListCell<Link>() {
        override fun updateItem(item: Link?, empty: Boolean) {
          super.updateItem(item, empty)
          text = item?.let {
            val arrow = if (it.isPredecessor) "←" else "→"
            val hardness = if (it.hardness == TaskDependency.Hardness.RUBBER) " (rubber)" else ""
            "$arrow  ${it.task.name}$hardness"
          }
        }
      }
    }
    listView.setOnMouseClicked { if (it.clickCount == 2) jumpToSelected() }
    listView.setOnKeyPressed { if (it.code == KeyCode.ENTER) jumpToSelected() }

    uiFacade.taskSelectionManager.addSelectionListener(object : TaskSelectionManager.Listener {
      override fun selectionChanged(currentSelection: MutableList<Task>?, source: Any?) {
        // Do not fight with the selection which this window has just made.
        if (source !== this@DependencyNavigator) {
          FXUtil.runLater { refresh() }
        }
      }

      override fun userInputConsumerChanged(newConsumer: Any?) {}
    })
  }

  fun show() {
    FXUtil.runLater {
      val stage = this.stage ?: createStage().also { this.stage = it }
      refresh()
      stage.show()
      stage.toFront()
    }
  }

  private fun createStage(): Stage {
    val content = VBox(header, listView).also {
      it.spacing = 6.0
      it.padding = Insets(10.0)
      VBox.setVgrow(listView, Priority.ALWAYS)
    }
    return Stage().also {
      it.title = "Dependencies"
      it.scene = Scene(content, 340.0, 260.0)
      it.isAlwaysOnTop = true
    }
  }

  private fun refresh() {
    val selection = uiFacade.taskSelectionManager.selectedTasks
    if (selection.size != 1) {
      header.text = if (selection.isEmpty()) "No task selected" else "${selection.size} tasks selected"
      links.clear()
      return
    }
    val task = selection[0]
    header.text = task.name
    links.setAll(collectLinks(task))
    if (links.isNotEmpty()) {
      listView.selectionModel.select(0)
    }
  }

  private fun collectLinks(task: Task): List<Link> {
    val result = mutableListOf<Link>()
    // The task depends on these, so they come before it.
    task.dependenciesAsDependant.toArray().forEach {
      result.add(Link(it.dependee, isPredecessor = true, hardness = it.hardness))
    }
    // These depend on the task, so they come after it.
    task.dependenciesAsDependee.toArray().forEach {
      result.add(Link(it.dependant, isPredecessor = false, hardness = it.hardness))
    }
    return result
  }

  private fun jumpToSelected() {
    val link = listView.selectionModel.selectedItem ?: return
    uiFacade.taskSelectionManager.setSelectedTasks(listOf(link.task), this)
    FXUtil.runLater { refresh() }
  }
}
