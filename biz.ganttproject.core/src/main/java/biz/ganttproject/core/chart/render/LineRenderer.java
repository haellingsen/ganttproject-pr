/*
GanttProject is an opensource project management tool.
Copyright (C) 2012 Dmitry Barashev, GanttProject Team

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
package biz.ganttproject.core.chart.render;

import biz.ganttproject.core.chart.canvas.Canvas;

import java.awt.*;
import java.util.Properties;

/**
 * Renders line shapes.
 *
 * @author dbarashev (Dmitry Barashev)
 */
public class LineRenderer {
  private final Properties myProperties;
  private Graphics2D myGraphics;

  public LineRenderer(Properties props) {
    myProperties = props;
  }

  public void setGraphics(Graphics2D graphics) {
    myGraphics = graphics;
  }

  public void renderLine(Canvas.Line line) {
    Graphics2D g = (Graphics2D) myGraphics.create();
    Style style = Style.getStyle(myProperties, line.getStyle());

    Style.Borders border = style.getBorder(line);
    g.setColor(border == null ? java.awt.Color.BLACK : border.getTop().getColor());
    g.setStroke(border == null ? Style.DEFAULT_STROKE : border.getTop().getStroke());
    g.drawLine(line.getStartX(), line.getStartY(), line.getFinishX(), line.getFinishY());
    if (line.getArrow() != Canvas.Arrow.NONE) {
      Canvas.Arrow arrow = line.getArrow();
      // The head follows the direction of the line, so diagonal segments get a proper head too.
      double dx = line.getFinishX() - line.getStartX();
      double dy = line.getFinishY() - line.getStartY();
      double len = Math.hypot(dx, dy);
      if (len == 0) {
        dx = 0; dy = 1; len = 1;
      }
      double ux = dx / len;
      double uy = dy / len;
      double baseX = line.getFinishX() - ux * arrow.getLength();
      double baseY = line.getFinishY() - uy * arrow.getLength();
      int[] xpoints = new int[] {
          line.getFinishX(),
          (int) Math.round(baseX - uy * arrow.getWidth()),
          (int) Math.round(baseX + uy * arrow.getWidth())};
      int[] ypoints = new int[] {
          line.getFinishY(),
          (int) Math.round(baseY + ux * arrow.getWidth()),
          (int) Math.round(baseY - ux * arrow.getWidth())};
      g.fillPolygon(xpoints, ypoints, 3);
    }
  }
}
