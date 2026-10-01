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

import net.sourceforge.ganttproject.TestSetupHelper
import net.sourceforge.ganttproject.task.TaskManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

class OverviewChartTest {
  private fun createProject(): TaskManager {
    val taskManager = TestSetupHelper.newTaskManagerBuilder().build()
    val start = TestSetupHelper.newMonday().time
    val phase = taskManager.newTaskBuilder().withName("Design").withParent(taskManager.rootTask)
      .withStartDate(start).build()
    val step = taskManager.newTaskBuilder().withName("Sketches").withParent(phase)
      .withStartDate(start).withDuration(taskManager.createLength(5)).build()
    taskManager.newTaskBuilder().withName("Detail <A&B>").withParent(step)
      .withStartDate(start).withDuration(taskManager.createLength(2)).build()
    val review = taskManager.newTaskBuilder().withName("Design review").withParent(step)
      .withStartDate(start).build()
    review.isMilestone = true
    taskManager.newTaskBuilder().withName("Build").withParent(taskManager.rootTask)
      .withStartDate(start).withDuration(taskManager.createLength(10)).withCompletion(50).build()
    return taskManager
  }

  @Test
  fun `detail level selects rows`() {
    val taskManager = createProject()
    assertEquals(listOf("Design", "Sketches", "Detail <A&B>", "Design review", "Build"),
      buildOverviewRows(taskManager, OverviewDetail.ALL).map { it.name })
    assertEquals(listOf("Design", "Sketches", "Design review", "Build"),
      buildOverviewRows(taskManager, OverviewDetail.PHASES).map { it.name })
    assertEquals(listOf("Design review"),
      buildOverviewRows(taskManager, OverviewDetail.MILESTONES).map { it.name })

    val rows = buildOverviewRows(taskManager, OverviewDetail.ALL)
    assertEquals(listOf(1, 2, 3, 3, 1), rows.map { it.depth })
    assertEquals(listOf(0, 0, 0, 0, 1), rows.map { it.group })
    assertTrue(rows[0].isSummary)
    assertTrue(rows[3].isMilestone)
    assertEquals(50, rows[4].completion)

    val monday = TestSetupHelper.newMonday()
    assertEquals(LocalDate.of(monday.get(java.util.Calendar.YEAR), monday.get(java.util.Calendar.MONTH) + 1,
      monday.get(java.util.Calendar.DAY_OF_MONTH)), rows[4].start, "dates must match the task calendar")

    val milestones = buildOverviewRows(taskManager, OverviewDetail.MILESTONES)
    assertEquals(listOf(1), milestones.map { it.depth }, "milestone view is flat")
  }

  @Test
  fun `renders well-formed svg`() {
    val rows = buildOverviewRows(createProject(), OverviewDetail.ALL)
    val today = rows.first().start.plusDays(1)
    val svg = OverviewSvgRenderer(rows, testLabels(), OverviewSettings(today = today)).render()
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray()))
    assertEquals("svg", doc.documentElement.tagName)
    assertTrue(svg.contains("Detail &lt;A&amp;B&gt;"))
    // milestone in the chart plus two in the legend
    assertEquals(3, doc.getElementsByTagName("polygon").length)
    assertTrue(svg.contains("Today"), "status date line is labelled")
  }

  @Test
  fun `renders empty project`() {
    val svg = OverviewSvgRenderer(emptyList(), testLabels(), OverviewSettings()).render()
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray()))
    assertTrue(svg.contains("No tasks"))
  }

  @Test
  fun `scale follows project span`() {
    val d = LocalDate.of(2026, 1, 1)
    // chart area is about 580 px wide on the A4 landscape page
    assertEquals(ScaleUnit.DAY to ScaleUnit.MONTH, chooseScale(d, d.plusDays(30), 580.0))
    assertEquals(ScaleUnit.WEEK to ScaleUnit.MONTH, chooseScale(d, d.plusDays(120), 580.0))
    assertEquals(ScaleUnit.MONTH to ScaleUnit.YEAR, chooseScale(d, d.plusYears(2), 580.0))
    assertEquals(ScaleUnit.QUARTER to ScaleUnit.YEAR, chooseScale(d, d.plusYears(5), 580.0))
    assertEquals(LocalDate.of(2026, 4, 1), ScaleUnit.QUARTER.floor(LocalDate.of(2026, 5, 17)))
    assertEquals(LocalDate.of(2025, 12, 29), ScaleUnit.WEEK.floor(d))
  }

  private fun testLabels() = OverviewLabels(
    title = "Test", statusDate = "Status", taskColumn = "Task", datesColumn = "Dates",
    completed = "Completed", remaining = "Remaining", milestone = "Milestone", milestoneReached = "Reached",
    critical = "Critical", today = "Today", noTasks = "No tasks", footer = "Footer",
    formatDate = { it.toString() }, monthName = { it.month.name.take(3) },
    weekLabel = { "W$it" }, quarterLabel = { "Q$it" },
  )
}
