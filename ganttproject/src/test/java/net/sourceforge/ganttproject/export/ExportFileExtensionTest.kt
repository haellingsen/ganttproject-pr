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
package net.sourceforge.ganttproject.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class ExportFileExtensionTest {
  private val exporter = ExporterToCSV()
  private val dir = File(System.getProperty("java.io.tmpdir"))

  @Test
  fun `appends extension when missing`() {
    assertEquals(File(dir, "plan.csv"), withExportExtension(File(dir, "plan"), exporter))
  }

  @Test
  fun `keeps a matching extension`() {
    assertEquals(File(dir, "plan.csv"), withExportExtension(File(dir, "plan.csv"), exporter))
    assertEquals(File(dir, "plan.CSV"), withExportExtension(File(dir, "plan.CSV"), exporter))
  }

  @Test
  fun `appends extension after an unrelated dot`() {
    assertEquals(File(dir, "plan v1.2.csv"), withExportExtension(File(dir, "plan v1.2"), exporter))
  }
}
