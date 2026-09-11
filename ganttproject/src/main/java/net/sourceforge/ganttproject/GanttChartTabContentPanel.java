/*
GanttProject is an opensource project management tool.
Copyright (C) 2005-2011 GanttProject Team

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 3
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.sourceforge.ganttproject;

import biz.ganttproject.app.*;
import biz.ganttproject.core.option.*;
import biz.ganttproject.ganttview.DependencyNavigator;
import biz.ganttproject.ganttview.TaskFilterActionSet;
import biz.ganttproject.ganttview.TaskTable;
import biz.ganttproject.task.TaskActions;
import com.google.common.base.Suppliers;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.sourceforge.ganttproject.action.BaselineDialogAction;
import net.sourceforge.ganttproject.action.CalculateCriticalPathAction;
import net.sourceforge.ganttproject.action.GPAction;
import net.sourceforge.ganttproject.chart.Chart;
import net.sourceforge.ganttproject.chart.ChartSelection;
import net.sourceforge.ganttproject.chart.gantt.GanttChartSelection;
import net.sourceforge.ganttproject.gui.UIConfiguration;
import net.sourceforge.ganttproject.gui.UIFacade;
import net.sourceforge.ganttproject.gui.UIUtil;
import net.sourceforge.ganttproject.gui.view.ViewProvider;
import net.sourceforge.ganttproject.language.GanttLanguage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

class GanttChartTabContentPanel extends ChartTabContentPanel implements ViewProvider {
  private final JComponent myGanttChart;
  private final UIFacade myWorkbenchFacade;
  private final CalculateCriticalPathAction myCriticalPathAction;
  private final BaselineDialogAction myBaselineAction;
  private final Supplier<TaskTable> myTaskTableSupplier;
  private final TaskActions myTaskActions;
  private final Function0<Unit> myInitializationCompleted;
  private final GPObservable<GPCursor> myCursorProperty;
  private final Consumer<MenuBuilder> myContextMenuBuilder;
  private TaskTable taskTable;
  private ViewComponents myViewComponents;
  private final GanttChartSelection mySelection;
  private final DoubleOption myDividerOption = new DefaultDoubleOption("divider", 0.5);
  private final DependencyNavigator myDependencyNavigator;
  // Whether the dependency panel is docked at the right hand side. Lives in the view options, so
  // it is remembered between sessions.
  private final BooleanOption myDependencyPaneOption = new DefaultBooleanOption("dependencyPane", true);
  // The panel width is kept in pixels rather than as a divider fraction: a fraction drifts every
  // time the window is resized, and the transient positions while the panel is being inserted
  // would be saved as if the user had dragged the divider.
  private final DoubleOption myDependencyWidthOption = new DefaultDoubleOption("dependencyPaneWidth", 480.0);
  private boolean isApplyingDependencyDivider = false;
  private static final double MIN_DEPENDENCY_PANE_WIDTH = 240.0;

  /** The panel is a sidebar. The table and the chart keep at least two thirds between them. */
  private static double maxDependencyPaneWidth(double splitWidth) {
    return Math.max(MIN_DEPENDENCY_PANE_WIDTH, splitWidth / 3);
  }
  private final GPOptionGroup myDependencyOptionGroup =
      new GPOptionGroup("ganttChartDependencyPane", myDependencyPaneOption, myDependencyWidthOption);

  GanttChartTabContentPanel(IGanttProject project, UIFacade workbenchFacade,
                            JComponent ganttChart,
                            GPObservable<GPCursor> cursorProperty,
                            Consumer<MenuBuilder> contextMenuBuilder,
                            UIConfiguration uiConfiguration, Supplier<TaskTable> taskTableSupplier,
                            TaskActions taskActions, BarrierEntrance initializationPromise) {
    super(project, workbenchFacade, workbenchFacade.getGanttChart());
    myInitializationCompleted = initializationPromise.register("Task table inserted into the component tree");
    myTaskActions = taskActions;
    myTaskTableSupplier = taskTableSupplier;
    myWorkbenchFacade = workbenchFacade;
    myGanttChart = ganttChart;
    myCursorProperty = cursorProperty;
    myContextMenuBuilder = contextMenuBuilder;
    // FIXME KeyStrokes of these 2 actions are not working...
    myCriticalPathAction = new CalculateCriticalPathAction(project.getTaskManager(), uiConfiguration, workbenchFacade);
    myCriticalPathAction.putValue(GPAction.TEXT_DISPLAY, ContentDisplay.TEXT_ONLY);
    myBaselineAction = new BaselineDialogAction(project, workbenchFacade);
    myBaselineAction.putValue(GPAction.TEXT_DISPLAY, ContentDisplay.TEXT_ONLY);

    setImageHeight(() -> Double.valueOf(myViewComponents.getImage().getHeight()).intValue());
    myDividerOption.addChangeValueListener(event -> {
      if (event.getNewValue() != event.getOldValue() && event.getTriggerID() != GanttChartTabContentPanel.this
        && myViewComponents != null) {
        myViewComponents.getSplitPane().setDividerPosition(0, myDividerOption.getValue());
      }
    });
    mySelection = new GanttChartSelection(project.getTaskManager(), workbenchFacade.getTaskSelectionManager());
    myDependencyNavigator = new DependencyNavigator(workbenchFacade, tasks -> {
      myTaskTableSupplier.get().revealTasks(tasks);
      return kotlin.Unit.INSTANCE;
    });
  }

  private FXToolbarBuilder createScheduleToolbar() {
    return new FXToolbarBuilder().withApplicationFont(FontKt.getApplicationFont())
      .addButton(myCriticalPathAction).addButton(myBaselineAction)
      .withClasses("toolbar-common", "toolbar-small", "toolbar-chart", "align-right");
  }

  private final Label filterTaskLabel = new Label();

  private final Supplier<TaskFilterActionSet> filterActions = Suppliers.memoize(() ->
    new TaskFilterActionSet(taskTable.getFilterManager(), taskTable.getCustomPropertyManager(), getProject().getProjectDatabase())
  );

  private FXToolbarBuilder createToolbarBuilder() {
    Button tableFilterButton = ToolbarKt.createButton(new TableButtonAction("taskTable.tableMenuFilter"), true);
    tableFilterButton.setOnAction(event -> {
      var tableFilterMenu = new ContextMenu();
      tableFilterMenu.getItems().clear();
      filterActions.get().tableFilterActions(new MenuBuilderFx(tableFilterMenu, null));
      tableFilterMenu.show(tableFilterButton, Side.BOTTOM, 0.0, 0.0);
      event.consume();
    });

    Button tableManageColumnButton = ToolbarKt.createButton(new TableButtonAction("taskTable.tableMenuToggle"), true);
    Objects.requireNonNull(tableManageColumnButton).setOnAction(event -> {
        myTaskActions.getManageColumnsAction().actionPerformed(null);
        event.consume();
    });

    HBox filterComponent = new HBox(0, filterTaskLabel, tableFilterButton, tableManageColumnButton);
    return new FXToolbarBuilder()
        .addButton(myTaskActions.getUnindentAction().asToolbarAction())
        .addButton(myTaskActions.getIndentAction().asToolbarAction())
        .addButton(myTaskActions.getMoveUpAction().asToolbarAction())
        .addButton(myTaskActions.getMoveDownAction().asToolbarAction())
        .addButton(myTaskActions.getLinkTasksAction().asToolbarAction())
        .addButton(myTaskActions.getUnlinkTasksAction().asToolbarAction())
        .addTail(filterComponent)
      //      it.toolbar.stylesheets.add("/net/sourceforge/ganttproject/ChartTabContentPanel.css")
//      it.toolbar.styleClass.remove("toolbar-big")

      .withClasses("toolbar-common", "toolbar-small", "task-filter");
  }

  @NotNull
  @Override
  public Function0<Unit> getRefresh() {
    return () -> {
      SwingUtilities.invokeLater(() -> {
        getChart().reset();
        myViewComponents.getChartNode().autosize();
      });
      return null;
    };
  }

  static class TableButtonAction extends GPAction {
    TableButtonAction(String id) {
      super(id);
      setFontAwesomeLabel(UIUtil.getFontawesomeLabel(this));
    }
    @Override
    public void actionPerformed(ActionEvent e) {
    }
  }

  @Override
  @NotNull
  public JComponent getChartComponent() {
    return myGanttChart;
  }

  private TaskTable setupTaskTable() {
    var taskTable = myTaskTableSupplier.get();
    taskTable.getHeaderHeightProperty().addListener((observable, oldValue, newValue) -> updateTimelineHeight());
    taskTable.getFilterManager().getHiddenTaskCount().addListener((obs,  oldValue,  newValue) -> Platform.runLater(() -> {
      if (newValue.intValue() != 0) {
        filterTaskLabel.setText(GanttLanguage.getInstance().formatText("taskTable.toolbar.tasksHidden", newValue.intValue()));
      } else {
        filterTaskLabel.setText("");
      }
    }));
    return taskTable;
  }


  @Override
  public @NotNull ChartSelection getSelection() {
    return mySelection;
  }

  @Override
  public Chart getChart() {
    return myWorkbenchFacade.getGanttChart();
  }

    @Override
  public Node getNode() {
    var image = myWorkbenchFacade.getLogo();
    var fxImage = (image instanceof BufferedImage bimg) ? SwingFXUtils.toFXImage(bimg, null) : null;
    myViewComponents = ViewPaneKt.createViewComponents(
      /*toolbarBuilder=*/      () -> {
        var toolbar = createToolbarBuilder().build().getToolbar$ganttproject();
        toolbar.getStylesheets().add("/net/sourceforge/ganttproject/ChartTabContentPanel.css");
        return toolbar;
      },
      /*tableBuilder=*/        () -> {
        taskTable = setupTaskTable();
        return taskTable.getTreeTable();
      },
      /*chartToolbarBuilder=*/ () -> {
        var chartToolbarBox = new HBox();
        var navigationBar = createNavigationToolbarBuilder().build().getToolbar$ganttproject();
        navigationBar.getStylesheets().add("/net/sourceforge/ganttproject/ChartTabContentPanel.css");
        chartToolbarBox.getChildren().add(navigationBar);
        HBox.setHgrow(navigationBar, Priority.ALWAYS);
        chartToolbarBox.getChildren().add(createScheduleToolbar().build().getToolbar$ganttproject());
        return chartToolbarBox;
      },
      /*chartBuilder=*/
      this::getChartComponent,
      myCursorProperty,
      this::buildContextMenu,
      fxImage,
      myWorkbenchFacade.getDpiOption()
    );

    setHeaderHeight(() -> taskTable.getHeaderHeightProperty().intValue());
    myViewComponents.getSplitPane().getDividers().get(0).positionProperty().addListener((observable, oldValue, newValue) ->
      myDividerOption.setValue(newValue.doubleValue(), GanttChartTabContentPanel.this)
    );
    taskTable.getColumnListWidthProperty().addListener((observable, oldValue, newValue) -> {
      myViewComponents.initializeDivider(taskTable.getColumnList().getTotalWidth());
    });
    taskTable.loadDefaultColumns();
    myInitializationCompleted.invoke();

    myDependencyPaneOption.addChangeValueListener(event -> Platform.runLater(this::updateDependencyPane));
    updateDependencyPane();
    return myViewComponents.getSplitPane();
  }

  /**
   * Docks the dependency panel at the right hand side, or takes it away again. The divider is put
   * back where the user last left it.
   */
  private void updateDependencyPane() {
    if (myViewComponents == null) {
      return;
    }
    var splitPane = myViewComponents.getSplitPane();
    // The table and the chart are the first two items, so the panel is the third one when it is on.
    boolean isVisible = splitPane.getItems().size() > 2;
    if (Boolean.TRUE.equals(myDependencyPaneOption.getValue())) {
      if (!isVisible) {
        var paneNode = myDependencyNavigator.getNode();
        SplitPane.setResizableWithParent(paneNode, Boolean.FALSE);
        // Worth restoring only when the split pane is already laid out. At startup the first divider
        // still holds its default and the table gets its width later, from the column widths.
        double tableDivider = splitPane.getWidth() > 0.0 && splitPane.getDividerPositions().length > 0
            ? splitPane.getDividerPositions()[0] : -1.0;
        // Inserting an item makes JavaFX redistribute the dividers, and those positions are not the
        // user's choice. Hold the flag over the insertion as well as over our own divider call.
        isApplyingDependencyDivider = true;
        splitPane.getItems().add(paneNode);
        applyDependencyWidth(tableDivider);
        // The stored width is in pixels, so the divider has to be recomputed whenever the window
        // changes size. Without this the panel would grow and shrink with the window.
        splitPane.widthProperty().addListener((observable, oldValue, newValue) -> applyDependencyWidth(-1.0));
        splitPane.getDividers().get(1).positionProperty().addListener((observable, oldValue, newValue) ->
          rememberDependencyWidth(newValue.doubleValue())
        );
      }
      myDependencyNavigator.refresh();
    } else if (isVisible) {
      double tableDivider = splitPane.getDividerPositions()[0];
      splitPane.getItems().remove(2);
      Platform.runLater(() -> splitPane.setDividerPosition(0, tableDivider));
    }
  }

  /**
   * Saves the width the user dragged the panel to. Positions which the code itself set, and the
   * degenerate ones which show up while the panel is being inserted, are not the user's choice.
   */
  private void rememberDependencyWidth(double dividerPosition) {
    if (isApplyingDependencyDivider || myViewComponents == null) {
      return;
    }
    var splitPane = myViewComponents.getSplitPane();
    if (splitPane.getItems().size() < 3 || splitPane.getWidth() <= 0.0) {
      return;
    }
    double width = (1.0 - dividerPosition) * splitPane.getWidth();
    if (width >= MIN_DEPENDENCY_PANE_WIDTH && width <= maxDependencyPaneWidth(splitPane.getWidth())) {
      myDependencyWidthOption.setValue(width, GanttChartTabContentPanel.this);
    }
  }

  /**
   * Gives the panel its stored width in pixels, taking the space from the chart. Pass a divider
   * position to put the task table back where it was, or a negative value to leave it alone.
   */
  private void applyDependencyWidth(double tableDivider) {
    var splitPane = myViewComponents.getSplitPane();
    Runnable apply = () -> {
      if (splitPane.getItems().size() < 3 || splitPane.getWidth() <= 0.0) {
        return;
      }
      double paneWidth = Math.max(MIN_DEPENDENCY_PANE_WIDTH,
          Math.min(myDependencyWidthOption.getValue(), maxDependencyPaneWidth(splitPane.getWidth())));
      double panelDivider = (splitPane.getWidth() - paneWidth) / splitPane.getWidth();
      isApplyingDependencyDivider = true;
      if (tableDivider >= 0.0) {
        splitPane.setDividerPositions(tableDivider, panelDivider);
      } else {
        splitPane.setDividerPosition(1, panelDivider);
      }
      Platform.runLater(() -> isApplyingDependencyDivider = false);
    };
    if (splitPane.getWidth() != 0.0) {
      Platform.runLater(apply);
    } else {
      splitPane.widthProperty().addListener(new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
          if (oldValue.doubleValue() == 0.0 && newValue.doubleValue() != 0.0) {
            splitPane.widthProperty().removeListener(this);
            Platform.runLater(apply);
          }
        }
      });
    }
  }

  /**
   * The dependency panel state goes into the application options rather than into the view
   * options: what a view writes into the project file is never read back, see
   * ViewSerializer.loadView.
   */
  @NotNull
  GPOptionGroup getOptionGroups() {
    return myDependencyOptionGroup;
  }

  @NotNull
  BooleanOption getDependencyPaneOption() {
    return myDependencyPaneOption;
  }

  private @NotNull Unit buildContextMenu(@NotNull MenuBuilder menuBuilder) {
    myContextMenuBuilder.accept(menuBuilder);
    return Unit.INSTANCE;
  }

  @NotNull
  @Override
  public List<GPOption<?>> getOptions() {
    var options = new ArrayList<GPOption<?>>();
    options.addAll(getProject().getTaskFilterManager().getOptions());
    options.add(myDividerOption);
    return options;
  }

  @Override
  public String getId() {
    return String.valueOf(UIFacade.GANTT_INDEX);
  }

  @Override
  public @NotNull GPAction getCreateAction() {
    return myTaskActions.getCreateAction();
  }

  @Override
  public @NotNull GPAction getDeleteAction() {
    return myTaskActions.getDeleteAction();
  }

  @Override
  public @NotNull GPAction getPropertiesAction() {
    return myTaskActions.getPropertiesAction();
  }
}
