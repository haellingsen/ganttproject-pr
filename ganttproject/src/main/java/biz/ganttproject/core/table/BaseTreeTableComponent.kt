/*
Copyright 2025 Dmitry Barashev,  BarD Software s.r.o

This file is part of GanttProject, an open-source project management tool.

GanttProject is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

GanttProject is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with GanttProject.  If not, see <http://www.gnu.org/licenses/>.
*/
package biz.ganttproject.core.table

import biz.ganttproject.FXUtil
import biz.ganttproject.app.*
import biz.ganttproject.customproperty.CustomPropertyClass
import biz.ganttproject.customproperty.CustomPropertyDefinition
import biz.ganttproject.customproperty.CustomPropertyHolder
import biz.ganttproject.customproperty.CustomPropertyManager
import biz.ganttproject.lib.fx.BuiltinColumns
import biz.ganttproject.lib.fx.ColumnListImpl
import biz.ganttproject.lib.fx.GPTreeTableView
import biz.ganttproject.lib.fx.depthFirstWalk
import javafx.beans.property.ReadOnlyDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventHandler
import javafx.scene.control.TreeItem
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import net.sourceforge.ganttproject.GPLogger
import net.sourceforge.ganttproject.IGanttProject
import net.sourceforge.ganttproject.ProjectEventListener
import net.sourceforge.ganttproject.ProjectOpenActivityFactory
import net.sourceforge.ganttproject.action.GPAction
import net.sourceforge.ganttproject.document.Document
import net.sourceforge.ganttproject.undo.GPUndoListener
import net.sourceforge.ganttproject.undo.GPUndoManager
import javax.swing.SwingUtilities
import javax.swing.event.UndoableEditEvent

/**
 * This is a base class for the tree tables in Gantt and Resource views. It defines type-parameterized methods
 * for working with the tree table items.
 */
abstract class BaseTreeTableComponent<NodeType, BuiltinColumnType: BuiltinColumn>(
  val treeTable: GPTreeTableView<NodeType>,
  internal val project: IGanttProject,
  private val undoManager: GPUndoManager,
  val customPropertyManager: CustomPropertyManager,
  val builtinColumns: BuiltinColumns,
  private val projectOpenActivityFactory: ProjectOpenActivityFactory
) {

  val headerHeightProperty: ReadOnlyDoubleProperty get() = treeTable.headerHeight
  val rowHeightProperty: ReadOnlyDoubleProperty get() = treeTable.fixedCellSizeProperty()

  protected var projectModified: () -> Unit = { project.isModified = true }
  lateinit var columnBuilder: ColumnBuilder<NodeType, BuiltinColumnType>
  val columnList: ColumnListImpl = ColumnListImpl(customPropertyManager,
    { treeTable.columns },
    { onColumnsChange() },
    { onColumnsChange() },
    builtinColumns
  )
  val columnListWidthProperty = SimpleObjectProperty<Pair<Double, Double>>()
  val rootItem: TreeItem<NodeType> = treeTable.root
  protected var areChangesIgnored = false

  init {
    FXUtil.runLater {
      treeTable.isShowRoot = false
      treeTable.isEditable = true
      treeTable.isTableMenuButtonVisible = false
    }
    treeTable.stylesheets.add("/biz/ganttproject/app/Dialog.css")
    treeTable.onProperties = this::onProperties
    treeTable.contextMenuActions = this::contextMenuActions
    columnList.totalWidthProperty.addListener { _, oldValue, newValue ->
      if (oldValue != newValue) {
        // We add vertical scroll bar width to the sum width of all columns, so that the split pane
        // which contains the table was resized appropriately.
        columnListWidthProperty.value = newValue.toDouble() to treeTable.vbarWidth()
      }
    }
    treeTable.onColumnResize = {
      columnList.onColumnResize
      projectModified()
    }
  }

  protected fun initProjectEventHandlers() {
    projectOpenActivityFactory.addBuilder { sm ->
      areChangesIgnored = true
      val tablesReadyStateEntrance = sm.stateTablesReady.register("Reload ${this.javaClass.simpleName}")
      sm.stateMainModelReady.await {
        treeTable.reload(::sync, tablesReadyStateEntrance)
      }
      sm.stateTablesReady.await {
        areChangesIgnored = false
        treeTable.updateWidth()
      }
      sm.stateCompleted.await {
        FXThread.runLater {
          treeTable.requestFocus()
        }
      }
      sm.stateCancelled.await {
        areChangesIgnored = false
        FXThread.runLater {
          treeTable.requestFocus()
        }
      }
      sm.stateFailed.await {
        areChangesIgnored = false
      }
    }
    project.addProjectEventListener(object : ProjectEventListener.Stub() {
      override fun projectRestoring(completion: Barrier<Document>) {
        completion.await {
          sync(keepFocus = true)
        }
      }

      override fun projectOpened(barrierRegistry: BarrierEntrance, barrier: Barrier<IGanttProject>) {
        //treeTable.reload(::sync, barrierRegistry.register("Reload Task Table"))
      }

      override fun projectCreated() {
        loadDefaultColumns()
        treeTable.reload(::sync)
      }
    })

    undoManager.addUndoableEditListener(object : GPUndoListener {
      override fun undoableEditHappened(e: UndoableEditEvent) {
        treeTable.coalescingRefresh()
      }

      override fun undoOrRedoHappened() {}
      override fun undoReset() {}
    })
  }

  protected fun initKeyboardEventHandlers(keyActions: List<GPAction>) {
    treeTable.addEventFilter(KeyEvent.KEY_PRESSED) { event ->
      // Tab toggles the focused row. While a cell is being edited, Tab keeps its editing meaning.
      if (treeTable.editingCell != null) return@addEventFilter
      event.whenMatches("tree.expand") {
        val focusedCell = treeTable.focusModel.focusedCell ?: return@whenMatches
        keepSelection(keepFocus = true) {
          focusedCell.treeItem.isExpanded = focusedCell.treeItem.isExpanded.not()
        }
      }
      event.whenMatches("tree.expandAll") {
        expandSubtrees(isExpanded = true)
      }
      event.whenMatches("tree.collapseAll") {
        expandSubtrees(isExpanded = false)
      }
      event.whenMatches("tree.cycle") {
        cycleOutline()
      }
      for (level in 1..MAX_OUTLINE_LEVEL) {
        event.whenMatches("tree.expandLevel$level") {
          expandToLevel(level)
        }
      }
    }
    treeTable.onKeyPressed = EventHandler { event ->
      keyActions.firstOrNull { action ->
        action.triggeredBy(event)
      }?.let { action ->
        SwingUtilities.invokeLater {
          undoManager.undoableEdit(action.name) {
            action.actionPerformed(null)
          }
        }
      }

      val focusedCell = treeTable.focusModel.focusedCell
      val column = focusedCell.tableColumn
      column?.userData?.let {
        if (column.isEditable && it is ColumnList.Column) {
          this.customPropertyManager.getCustomPropertyDefinition(it.id)?.let { def ->
            if (def.propertyClass == CustomPropertyClass.BOOLEAN) {
              if (event.code == KeyCode.SPACE || event.code == KeyCode.ENTER && event.getModifiers() == 0) {
                val task = focusedCell.treeItem.value
                // intentionally java.lang.Boolean, because as? Boolean returns null
                (tableModel.getValue(task, def) as? java.lang.Boolean)?.let { value ->
                  undoManager.undoableEdit("Edit properties") {
                    tableModel.setValue(value.booleanValue().not(), task, def)
                  }
                  treeTable.refreshFocusedCell()
                }
              }
            }
          }
        }
      }
    }
  }

  abstract fun isTreeColumn(column: BuiltinColumnType): Boolean
  abstract fun getCustomValues(node: NodeType): CustomPropertyHolder
  abstract fun loadDefaultColumns()
  protected abstract fun sync(keepFocus: Boolean = false)
  protected abstract fun onProperties()
  fun onColumnsChange()  {
    FXUtil.runLater {
      columnList.columns().forEach { builtinColumns.find(it.id)?.stub?.isVisible = it.isVisible }
      val newColumns = columnBuilder.buildColumns(
        columns = columnList.columns(),
        currentColumns = treeTable.columns.map { it.userData as ColumnList.Column }.toList(),
      )
      if (newColumns.isNotEmpty()) {
        keepSelection {
          treeTable.reload(::sync, null)
          treeTable.setColumns(newColumns)
        }
      }
    }
  }

  protected abstract fun contextMenuActions(builder: MenuBuilder)
  abstract val tableModel: TableModel<NodeType, BuiltinColumnType>
  protected abstract val selectionKeeper: SelectionKeeper<NodeType>
  protected fun keepSelection(keepFocus: Boolean = false, code: () -> Unit) {
    selectionKeeper.keepSelection(keepFocus, code)
  }

  /**
   * Expands or collapses every row in the tree, whatever is selected. Use the arrow on a row, or
   * [expandToLevel], to work on a part of the outline.
   */
  fun expandSubtrees(isExpanded: Boolean) {
    keepSelection(keepFocus = true) {
      treeTable.root.depthFirstWalk {
        it.isExpanded = isExpanded
        return@depthFirstWalk true
      }
    }
  }

  /**
   * Shows [level] levels of the outline: the rows above that depth are expanded and the rest is
   * collapsed. Level 1 leaves only the topmost rows.
   */
  fun expandToLevel(level: Int) {
    keepSelection(keepFocus = true) {
      expandBelow(treeTable.root, depth = 0, level = level)
    }
  }

  /**
   * Shift+Tab as in org-mode: each press shows one more level of the outline, and after the whole
   * tree is open the next press folds it back to the topmost rows. The current level is read from
   * the tree itself, so the cycle picks up wherever the user left the outline by other means.
   * Never more than [CYCLE_LEVELS] steps: a deeper tree jumps from level 4 to fully open.
   */
  fun cycleOutline() {
    val deepest = treeDepth(treeTable.root, 0)
    if (deepest <= 1) return
    val last = minOf(deepest, CYCLE_LEVELS)
    val current = openLevels()
    when {
      current >= last -> expandSubtrees(isExpanded = false)
      current + 1 >= last -> expandSubtrees(isExpanded = true)
      else -> expandToLevel(current + 1)
    }
  }

  /** How many levels are open: the deepest N such that every parent above depth N is expanded. */
  private fun openLevels(): Int {
    var level = 1
    while (level < CYCLE_LEVELS && allExpandedAbove(treeTable.root, 0, level + 1)) level++
    return level
  }

  private fun allExpandedAbove(item: TreeItem<NodeType>, depth: Int, level: Int): Boolean =
    item.children.all { child ->
      child.children.isEmpty() || depth + 1 >= level || (child.isExpanded && allExpandedAbove(child, depth + 1, level))
    }

  private fun treeDepth(item: TreeItem<NodeType>, depth: Int): Int =
    item.children.maxOfOrNull { treeDepth(it, depth + 1) } ?: depth

  private fun expandBelow(item: TreeItem<NodeType>, depth: Int, level: Int) {
    item.children.forEach { child ->
      child.isExpanded = depth + 1 < level
      expandBelow(child, depth + 1, level)
    }
  }
}

/** The deepest outline level which has a shortcut of its own. */
const val MAX_OUTLINE_LEVEL = 9

/** The number of steps Shift+Tab walks through before it folds everything again. */
const val CYCLE_LEVELS = 5


/**
 * Interface of the table model which provides getters and setters of the values shown and changed in the tree table.
 */
interface TableModel<NodeType, DefaultColumnType: BuiltinColumn> {
  fun getValueAt(t: NodeType, defaultColumn: DefaultColumnType): Any?
  fun getValue(t: NodeType, customProperty: CustomPropertyDefinition): Any?
  fun setValue(value: Any, node: NodeType, property: DefaultColumnType)
  fun setValue(value: Any, node: NodeType, column: CustomPropertyDefinition)
}

fun <T> GPTreeTableView<T>.reload(sync: ()->Unit, termination: OnBarrierReached? = null) {
  FXUtil.runLater {
    this.root.children.clear()
    this.selectionModel.clearSelection()
    sync()
    termination?.invoke()
  }
}

internal val LOGGER = GPLogger.create("BaseTreeTable")