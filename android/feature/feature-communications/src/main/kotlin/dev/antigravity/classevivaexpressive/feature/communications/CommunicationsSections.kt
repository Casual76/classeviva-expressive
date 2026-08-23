package dev.antigravity.classevivaexpressive.feature.communications

import dev.antigravity.classevivaexpressive.core.domain.model.Communication
import dev.antigravity.classevivaexpressive.core.domain.model.Note
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import dev.antigravity.fluidengine.ui.fluid.FluidSectionAnchor

internal data class ArchiveMonthSection<T>(
  val key: String,
  val label: String,
  val items: List<T>,
)

/** Groups an archive without changing the repository order, while keeping stable month identities. */
internal fun <T> archiveMonthSections(
  items: List<T>,
  dateOf: (T) -> String,
  locale: Locale = Locale.forLanguageTag("it-IT"),
): List<ArchiveMonthSection<T>> {
  if (items.isEmpty()) return emptyList()

  val buckets = linkedMapOf<YearMonth?, MutableList<T>>()
  items.forEach { item ->
    val month = dateOf(item).toArchiveLocalDate()?.let(YearMonth::from)
    buckets.getOrPut(month) { mutableListOf() }.add(item)
  }

  val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
  return buckets.map { (month, values) ->
    ArchiveMonthSection(
      key = month?.toString() ?: "unknown-date",
      label = month
        ?.format(monthFormatter)
        ?.replaceFirstChar { first ->
          if (first.isLowerCase()) first.titlecase(locale) else first.toString()
        }
        ?: "Senza data",
      items = values,
    )
  }
}

private fun String.toArchiveLocalDate(): LocalDate? {
  val candidate = trim().take(10)
  return runCatching { LocalDate.parse(candidate) }.getOrNull()
}

/** LazyColumn indices include FluidScreen's title and the static controls above the archive. */
internal fun communicationSectionAnchors(
  sections: List<ArchiveMonthSection<Communication>>,
  includeMarkAllReadAction: Boolean,
  includeUnreadAnchor: Boolean,
): List<FluidSectionAnchor> {
  var itemIndex = 4 + if (includeMarkAllReadAction) 1 else 0
  var unreadAdded = false
  val anchors = buildList {
    sections.forEach { section ->
      add(FluidSectionAnchor("month:${section.key}", section.label, itemIndex))
      if (includeUnreadAnchor && !unreadAdded) {
        val firstUnreadIndex = section.items.indexOfFirst { !it.read }
        if (firstUnreadIndex >= 0) {
          add(
            FluidSectionAnchor(
              key = "unread",
              label = "Non lette",
              itemIndex = itemIndex + 1 + firstUnreadIndex,
            ),
          )
          unreadAdded = true
        }
      }
      itemIndex += 1 + section.items.size
    }
  }
  return anchors.sortedBy(FluidSectionAnchor::itemIndex)
}

internal fun noteSectionAnchors(
  sections: List<ArchiveMonthSection<Note>>,
): List<FluidSectionAnchor> {
  var itemIndex = 3
  return sections.map { section ->
    FluidSectionAnchor(
      key = "month:${section.key}",
      label = section.label,
      itemIndex = itemIndex,
    ).also {
      itemIndex += 1 + section.items.size
    }
  }
}
