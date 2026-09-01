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
package biz.ganttproject.core.option

import biz.ganttproject.core.chart.render.Style
import javafx.util.StringConverter

/**
 * Adapters from the options which were written before the ObservableProperty model to that model,
 * so that they can render themselves into a property pane with PropertyPaneBuilder.
 *
 * The value is mirrored in both directions: an edit in the property pane reaches the option, and a
 * change of the option updates the editor. The property is passed as the trigger of its own writes
 * and the option as the trigger of the writes coming back, which is what stops the two sides from
 * echoing each other.
 */
fun BooleanOption.toObservable(): ObservableBoolean =
  ObservableBoolean(id, value ?: false).also { property ->
    addChangeValueListener { event ->
      if (event.triggerID !== property) {
        property.set(value ?: false, trigger = property)
      }
    }
    property.addWatcher { event ->
      if (event.trigger !== this) {
        setValue(event.newValue, this)
      }
    }
    property.mirrorWritability(this)
  }

fun StringOption.toObservable(): ObservableString =
  ObservableString(id, value, isScreened = isScreened).also { property ->
    addChangeValueListener { event ->
      if (event.triggerID !== property) {
        property.set(value, trigger = property)
      }
    }
    property.addWatcher { event ->
      if (event.trigger !== this) {
        setValue(event.newValue, this)
      }
    }
    property.mirrorWritability(this)
  }

fun ColorOption.toObservable(): ObservableColor =
  ObservableColor(id, value.toStyleColor()).also { property ->
    addChangeValueListener { event ->
      if (event.triggerID !== property) {
        property.set(value.toStyleColor(), trigger = property)
      }
    }
    property.addWatcher { event ->
      if (event.trigger !== this) {
        setValue(event.newValue?.get(), this)
      }
    }
    property.mirrorWritability(this)
  }

private fun java.awt.Color?.toStyleColor(): Style.Color? =
  this?.let { Style.Color.parse(ColorOption.Util.getColor(it)) }

private fun <T> ObservableProperty<T>.mirrorWritability(option: GPOption<*>) {
  setWritable(option.isWritable)
  option.isWritableProperty.addWatcher { setWritable(it.newValue) }
}

fun DoubleOption.toObservable(): ObservableDouble =
  ObservableDouble(id, value ?: 0.0).also { property ->
    addChangeValueListener { event ->
      if (event.triggerID !== property) {
        property.set(value ?: 0.0, trigger = property)
      }
    }
    property.addWatcher { event ->
      if (event.trigger !== this) {
        setValue(event.newValue, this)
      }
    }
    property.mirrorWritability(this)
  }

/**
 * Renders a font option as the family and the size, which is what the Swing page did with a
 * combo box and a slider. There is no single editor for a font specification, so the two parts
 * are separate rows and each of them writes a whole new specification back.
 */
fun FontOption.visitFontPane(builder: PropertyPaneBuilder) {
  val families = fontFamilies
  val family = ObservableChoice(id, value?.family, families, object : StringConverter<String?>() {
    override fun toString(item: String?) = item ?: ""
    override fun fromString(string: String?) = string
  })
  family.addWatcher { event ->
    event.newValue?.let { setValue(FontSpec(it, value?.size ?: FontSpec.Size.NORMAL), this) }
  }
  builder.dropdown(family, null)

  val labels = sizeLabels
  val sizes = FontSpec.Size.entries.toList()
  val size = ObservableChoice("$id.size", value?.size, sizes, object : StringConverter<FontSpec.Size?>() {
    override fun toString(item: FontSpec.Size?) = item?.let { labels[it] ?: it.name } ?: ""
    override fun fromString(string: String?) = sizes.firstOrNull { labels[it] == string || it.name == string }
  })
  size.addWatcher { event ->
    event.newValue?.let { setValue(FontSpec(value?.family ?: families.first(), it), this) }
  }
  // The size row continues the row above it and needs no label of its own, in the same way as the
  // slider under the font combo box in the Swing page.
  builder.dropdown(size) { labelText = "" }
}
