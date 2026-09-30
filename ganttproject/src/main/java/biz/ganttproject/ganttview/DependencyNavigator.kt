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
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.SelectionMode
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.scene.control.TableRow
import javafx.scene.control.TableView
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import net.sourceforge.ganttproject.gui.UIFacade
import net.sourceforge.ganttproject.task.Task
import net.sourceforge.ganttproject.task.TaskManager
import net.sourceforge.ganttproject.task.TaskSelectionManager
import net.sourceforge.ganttproject.task.dependency.TaskDependency
import net.sourceforge.ganttproject.task.dependency.TaskDependencyException
import net.sourceforge.ganttproject.task.event.TaskListenerAdapter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A panel which lists the dependencies of the selected task: the tasks it waits for and the tasks
 * which wait for it. Activating a row selects that task, so the panel is a way of walking the
 * dependency chain without hunting for the lines in the chart.
 *
 * It also edits the list. Delete removes the highlighted dependency, and the search field at the
 * bottom finds another task by id or name and links it as a predecessor (Enter) or a successor
 * (Shift+Enter). Both go through the undo manager.
 *
 * It is docked at the right hand side of the Gantt view; [node] is what the view embeds. The panel
 * follows the selection on its own, so the view only has to decide whether it is on screen.
 */
class DependencyNavigator(private val uiFacade: UIFacade, private val revealTasks: (List<Task>) -> Unit) {
  private data class Link(
    val task: Task,
    val isPredecessor: Boolean,
    val dependency: TaskDependency,
    val parentName: String
  )

  private val links = FXCollections.observableArrayList<Link>()
  private val table = TableView(links)
  private val header = Label().also { it.styleClass.add("dependency-navigator-header") }
  private val status = Label()

  private val searchField = TextField().also { it.promptText = "Find a task to link: id or name…" }
  private val candidates = FXCollections.observableArrayList<Task>()
  private val candidateView = ListView(candidates).also {
    it.prefHeight = 110.0
    it.isVisible = false
    it.isManaged = false
  }
  private val addBeforeButton = Button("← Before").also {
    it.tooltip = Tooltip("The found task runs before the selected one (Enter)")
  }
  private val addAfterButton = Button("After →").also {
    it.tooltip = Tooltip("The found task waits for the selected one (Shift+Enter)")
  }
  private val removeButton = Button("Remove").also {
    it.tooltip = Tooltip("Remove the highlighted dependency (Delete)")
  }
  private val revealButton = Button("Show all in chart").also {
    it.tooltip = Tooltip("Expand the tree so that every task in this list has a bar in the chart")
  }

  /** The task manager we listen to, so that the list follows undo and edits made in the chart. */
  private var listenedManager: TaskManager? = null

  /** The node to dock. Built once, on first access. */
  val node: Node by lazy { build() }

  init {
    uiFacade.taskSelectionManager.addSelectionListener(object : TaskSelectionManager.Listener {
      override fun selectionChanged(currentSelection: MutableList<Task>?, source: Any?) {
        // Do not fight with the selection which this panel has just made.
        if (source !== this@DependencyNavigator) {
          scheduleRefresh()
        }
      }

      override fun userInputConsumerChanged(newConsumer: Any?) {}
    })
  }

  private fun selectedTask(): Task? =
    uiFacade.taskSelectionManager.selectedTasks.let { if (it.size == 1) it[0] else null }

  /** Reads the current selection into the table. Call it when the panel becomes visible. */
  fun refresh() {
    val selection = uiFacade.taskSelectionManager.selectedTasks
    val task = selectedTask()
    if (task == null) {
      header.text = if (selection.isEmpty()) "No task selected" else "${selection.size} tasks selected"
      links.clear()
      updateCandidates(null)
      return
    }
    listenTo(task.manager)
    val previouslySelected = table.selectionModel.selectedItem?.dependency
    header.text = "${task.taskID}  ${task.name}"
    links.setAll(collectLinks(task))
    val keep = links.indexOfFirst { it.dependency === previouslySelected }
    if (links.isNotEmpty()) {
      table.selectionModel.select(if (keep >= 0) keep else 0)
    }
    updateCandidates(searchField.text)
  }

  private fun listenTo(manager: TaskManager) {
    if (listenedManager === manager) return
    listenedManager = manager
    manager.addTaskListener(TaskListenerAdapter { scheduleRefresh() })
  }

  /** Set while a refresh is queued, so that a burst of events ends in one refresh. */
  private val isRefreshPending = AtomicBoolean(false)

  /**
   * Queues one refresh on the JavaFX thread. A paste or an undo fires one event per task, dependency
   * and schedule change, and a refresh for each of them kept the JavaFX thread busy long after the
   * edit was done. The delay lets the burst finish first.
   */
  private fun scheduleRefresh() {
    if (isRefreshPending.compareAndSet(false, true)) {
      FXUtil.runLater(REFRESH_DELAY_MS) {
        isRefreshPending.set(false)
        if (node.scene != null) refresh()
      }
    }
  }

  private fun build(): Node {
    val directionColumn = TableColumn<Link, Link>("").also { column ->
      column.setCellValueFactory { SimpleObjectProperty(it.value) }
      column.setCellFactory {
        object : TableCell<Link, Link>() {
          override fun updateItem(item: Link?, empty: Boolean) {
            super.updateItem(item, empty)
            alignment = Pos.CENTER
            text = item?.let { if (it.isPredecessor) "←" else "→" }
            tooltip = item?.let {
              val direction = if (it.isPredecessor) "Runs before this task" else "Waits for this task"
              val hardness = if (it.dependency.hardness == TaskDependency.Hardness.RUBBER) "rubber" else "hard"
              Tooltip("$direction, $hardness")
            }
          }
        }
      }
      column.prefWidth = 30.0
      column.isSortable = false
      column.isResizable = false
    }
    val idColumn = TableColumn<Link, String>("Id").also { column ->
      column.setCellValueFactory { SimpleStringProperty(it.value.task.taskID.toString()) }
      column.prefWidth = 52.0
    }
    val nameColumn = TableColumn<Link, String>("Name").also { column ->
      column.setCellValueFactory { SimpleStringProperty(it.value.task.name) }
      column.prefWidth = 150.0
    }
    val parentColumn = TableColumn<Link, String>("Parent").also { column ->
      column.setCellValueFactory { SimpleStringProperty(it.value.parentName) }
      column.prefWidth = 120.0
    }
    val typeColumn = TableColumn<Link, String>("Type").also { column ->
      column.setCellValueFactory {
        SimpleStringProperty(if (it.value.dependency.hardness == TaskDependency.Hardness.RUBBER) "rubber" else "hard")
      }
      column.prefWidth = 60.0
    }
    table.columns.setAll(directionColumn, idColumn, parentColumn, nameColumn, typeColumn)
    table.selectionModel.selectionMode = SelectionMode.SINGLE
    table.placeholder = Label("No dependencies")
    // Only a double click on a row jumps. Listening on the whole table also caught double clicks on
    // the column headers, so resizing a column to fit sent the user off to the highlighted task.
    table.setRowFactory {
      TableRow<Link>().also { row ->
        row.setOnMouseClicked { if (it.clickCount == 2 && !row.isEmpty) jumpToSelected() }
      }
    }
    table.setOnKeyPressed {
      when (it.code) {
        KeyCode.ENTER -> jumpToSelected()
        KeyCode.DELETE, KeyCode.BACK_SPACE -> removeSelected()
        else -> return@setOnKeyPressed
      }
      it.consume()
    }
    removeButton.setOnAction { removeSelected() }
    removeButton.disableProperty().bind(table.selectionModel.selectedItemProperty().isNull)
    revealButton.setOnAction { revealAll() }
    revealButton.disableProperty().bind(Bindings.isEmpty(links))

    candidateView.setCellFactory {
      object : ListCell<Task>() {
        override fun updateItem(item: Task?, empty: Boolean) {
          super.updateItem(item, empty)
          text = item?.let {
            val path = parentPath(it)
            if (path.isEmpty()) "${it.taskID}  ${it.name}" else "${it.taskID}  ${it.name}   — $path"
          }
        }
      }
    }
    searchField.textProperty().addListener { _, _, text -> updateCandidates(text) }
    searchField.addEventFilter(KeyEvent.KEY_PRESSED) { onSearchKey(it) }
    candidateView.addEventFilter(KeyEvent.KEY_PRESSED) { onSearchKey(it) }
    candidateView.setOnMouseClicked { if (it.clickCount == 2) addCandidate(asPredecessor = true) }
    addBeforeButton.setOnAction { addCandidate(asPredecessor = true) }
    addAfterButton.setOnAction { addCandidate(asPredecessor = false) }
    val noCandidate = candidateView.selectionModel.selectedItemProperty().isNull
    addBeforeButton.disableProperty().bind(noCandidate)
    addAfterButton.disableProperty().bind(noCandidate)

    val removeRow = HBox(6.0, status, revealButton, removeButton).also {
      it.alignment = Pos.CENTER_LEFT
      HBox.setHgrow(status, Priority.ALWAYS)
      status.maxWidth = Double.MAX_VALUE
    }
    val addRow = HBox(6.0, searchField, addBeforeButton, addAfterButton).also {
      HBox.setHgrow(searchField, Priority.ALWAYS)
    }
    return VBox(header, table, removeRow, addRow, candidateView).also {
      it.spacing = 4.0
      it.padding = Insets(6.0)
      it.styleClass.add("dependency-navigator")
      // Wide enough for the id and two names; the user can drag the divider from here.
      it.minWidth = 240.0
      it.prefWidth = 480.0
      VBox.setVgrow(table, Priority.ALWAYS)
    }
  }

  private fun collectLinks(task: Task): List<Link> {
    val result = mutableListOf<Link>()
    // The task depends on these, so they come before it.
    task.dependenciesAsDependant.toArray().forEach {
      result.add(Link(it.dependee, isPredecessor = true, dependency = it, parentName = parentPath(it.dependee)))
    }
    // These depend on the task, so they come after it.
    task.dependenciesAsDependee.toArray().forEach {
      result.add(Link(it.dependant, isPredecessor = false, dependency = it, parentName = parentPath(it.dependant)))
    }
    return result
  }

  /**
   * The name of the task one level up. The root task holds the whole project and is not a row the
   * user ever sees, so a top level task reports no parent at all.
   */

  /** All the parents, outermost first: "Phase > Work package > Group". Empty for a top level task. */
  private fun parentPath(task: Task): String {
    val names = mutableListOf<String>()
    var parent = task.supertask
    while (parent != null && parent !== task.manager.rootTask) {
      names.add(0, parent.name)
      parent = parent.supertask
    }
    return names.joinToString(" > ")
  }

  private fun jumpToSelected() {
    val link = table.selectionModel.selectedItem ?: return
    uiFacade.taskSelectionManager.setSelectedTasks(listOf(link.task), this)
    FXUtil.runLater { refresh() }
  }

  /** Makes the selected task and everything it is linked to visible in the tree and the chart. */
  private fun revealAll() {
    val task = selectedTask() ?: return
    revealTasks(listOf(task) + links.map { it.task })
    status.text = "Expanded to ${links.size} linked task${if (links.size == 1) "" else "s"}"
  }

  private fun removeSelected() {
    val link = table.selectionModel.selectedItem ?: return
    val nextIndex = table.selectionModel.selectedIndex.coerceAtMost(links.size - 2)
    uiFacade.undoManager.undoableEdit("Remove dependency") {
      link.dependency.delete()
    }
    status.text = "Removed ${arrowText(link)}"
    uiFacade.taskSelectionManager.fireSelectionChanged()
    refresh()
    if (nextIndex >= 0 && links.isNotEmpty()) {
      table.selectionModel.select(nextIndex)
    }
  }

  /**
   * Tasks matching the typed text, in document order, minus the selected task and the ones it is
   * already linked to. Digits match the id, anything else the words of the name and the parents.
   */
  private fun updateCandidates(text: String?) {
    val task = selectedTask()
    val query = text?.trim().orEmpty()
    if (task == null || query.isEmpty()) {
      candidates.clear()
    } else {
      val linked = links.map { it.task }.toSet()
      val id = query.toIntOrNull()
      // Every word has to appear somewhere in "parents > name", so "mt tool com" finds
      // "MT Tool Base > EL Completion" even though no single field holds the whole phrase.
      val words = query.split(' ').filter { it.isNotEmpty() }
      candidates.setAll(task.manager.taskHierarchy.tasksInDocumentOrder.filter {
        it !== task && it !in linked &&
          (if (id != null) it.taskID.toString().startsWith(query)
           else "${parentPath(it)} ${it.name}".let { text -> words.all { word -> text.contains(word, ignoreCase = true) } })
      })
      if (candidates.isNotEmpty()) candidateView.selectionModel.select(0)
    }
    val show = candidates.isNotEmpty()
    candidateView.isVisible = show
    candidateView.isManaged = show
  }

  private fun onSearchKey(e: KeyEvent) {
    when (e.code) {
      KeyCode.ENTER -> addCandidate(asPredecessor = !e.isShiftDown)
      KeyCode.DOWN -> if (e.source === searchField && candidates.isNotEmpty()) {
        candidateView.requestFocus()
        if (candidateView.selectionModel.selectedIndex < 0) candidateView.selectionModel.select(0)
      } else return
      KeyCode.ESCAPE -> if (searchField.text.isNullOrEmpty()) return else searchField.clear()
      else -> return
    }
    e.consume()
  }

  private fun addCandidate(asPredecessor: Boolean) {
    val task = selectedTask() ?: return
    val other = candidateView.selectionModel.selectedItem ?: return
    val dependant = if (asPredecessor) task else other
    val dependee = if (asPredecessor) other else task
    val manager = task.manager
    val collection = manager.dependencyCollection
    if (!collection.canCreateDependency(dependant, dependee)) {
      status.text = "Cannot link ${other.name}: it would form a loop or nest a subtask"
      return
    }
    var created: TaskDependency? = null
    uiFacade.undoManager.undoableEdit("Add dependency") {
      try {
        created = collection.createDependency(dependant, dependee).also {
          it.hardness = TaskDependency.Hardness.parse(manager.dependencyHardnessOption.value)
        }
      } catch (ex: TaskDependencyException) {
        status.text = ex.message ?: "Cannot create the dependency"
      }
    }
    created?.let { dep ->
      status.text = "Added ${arrowText(Link(other, asPredecessor, dep, ""))}"
      searchField.clear()
      uiFacade.taskSelectionManager.fireSelectionChanged()
      refresh()
      links.indexOfFirst { it.dependency === dep }.takeIf { it >= 0 }?.let { table.selectionModel.select(it) }
      searchField.requestFocus()
    }
  }

  private fun arrowText(link: Link): String {
    val self = selectedTask()?.name ?: "task"
    return if (link.isPredecessor) "${link.task.name} → $self" else "$self → ${link.task.name}"
  }
}

/** How long the panel waits for a burst of task events to end before it refreshes. */
private const val REFRESH_DELAY_MS = 100L
