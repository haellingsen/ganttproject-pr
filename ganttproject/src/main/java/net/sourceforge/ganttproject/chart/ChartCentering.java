/*
Copyright 2026 BarD Software s.r.o, Dmitry Barashev

This file is part of GanttProject, an opensource project management tool.

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
package net.sourceforge.ganttproject.chart;

import biz.ganttproject.core.chart.grid.Offset;
import biz.ganttproject.core.time.TimeDuration;

import java.awt.Dimension;
import java.util.Date;

/**
 * Puts a date in the middle of a timeline chart rather than at its left edge, which is what
 * {@link TimelineChart#setStartDate} does on its own.
 *
 * <p>Scrolling to a point of interest and zooming both go through here, so the thing the user is
 * looking at keeps its place on screen instead of sliding out of view.
 */
public class ChartCentering {
  private ChartCentering() {
  }

  /** The date halfway across the chart, or the start date when the chart has no size yet. */
  public static Date getCenterDate(TimelineChart chart) {
    ChartModel model = chart.getModel();
    Dimension bounds = model == null ? null : model.getBounds();
    if (bounds == null || bounds.width <= 0) {
      return chart.getStartDate();
    }
    Offset offset = model.getOffsetAt(bounds.width / 2);
    return offset == null ? chart.getStartDate() : offset.getOffsetStart();
  }

  /** Scrolls so that the given date sits in the middle of the chart. */
  public static void centerOn(TimelineChart chart, Date date) {
    if (date == null) {
      return;
    }
    chart.setStartDate(date);
    ChartModel model = chart.getModel();
    Dimension bounds = model == null ? null : model.getBounds();
    if (bounds == null || bounds.width <= 0) {
      // No size to centre within yet. The date is at the left edge, which is the old behaviour.
      return;
    }
    Offset offset = model.getOffsetAt(bounds.width / 2);
    if (offset == null) {
      return;
    }
    // With the date at the left edge, the span from it to the middle is half a viewport. Going back
    // by that much leaves the date in the middle.
    TimeDuration halfViewport = model.getTimeUnitStack().createDuration(
        model.getBottomUnit(), date, offset.getOffsetStart());
    chart.scrollBy(halfViewport.reverse());
  }
}
