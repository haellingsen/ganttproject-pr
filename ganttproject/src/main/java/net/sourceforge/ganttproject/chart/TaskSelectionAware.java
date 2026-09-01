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
package net.sourceforge.ganttproject.chart;

import net.sourceforge.ganttproject.task.TaskSelectionManager;

/**
 * A chart which takes part in the task selection shared by the views. The view manager hands the
 * selection manager to the charts which implement this, so that a chart in a plugin can select a
 * task without the plugin reaching into the application.
 */
public interface TaskSelectionAware {
  void setTaskSelectionManager(TaskSelectionManager selectionManager);
}
