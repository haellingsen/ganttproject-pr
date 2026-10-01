/*
 * GanttProject is an opensource project management tool. License: GPL3
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package biz.ganttproject.ganttview

import biz.ganttproject.core.model.task.ConstraintType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LinkTypeTest {
  @Test
  fun `each link type creates the matching constraint`() {
    val expected = mapOf(
      LinkType.FS to ConstraintType.finishstart,
      LinkType.SS to ConstraintType.startstart,
      LinkType.FF to ConstraintType.finishfinish,
      LinkType.SF to ConstraintType.startfinish,
    )
    expected.forEach { (linkType, constraintType) ->
      val constraint = linkType.create()
      assertEquals(constraintType, constraint.type)
      assertEquals(linkType, LinkType.of(constraint))
    }
  }
}
