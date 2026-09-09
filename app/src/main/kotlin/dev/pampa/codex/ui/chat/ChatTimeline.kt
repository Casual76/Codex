package dev.pampa.codex.ui.chat

import dev.pampa.codex.data.chat.ChatMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Una riga della conversazione: o un messaggio, o il giorno in cui i messaggi che seguono sono
 * arrivati.
 */
sealed interface TimelineRow {
  val key: String

  data class Day(val date: LocalDate) : TimelineRow {
    override val key: String get() = "day-$date"
  }

  data class Message(val message: ChatMessage) : TimelineRow {
    override val key: String get() = message.id
  }
}

/**
 * Da una lista di messaggi alla lista che si disegna.
 *
 * I separatori si calcolano qui e non dentro la schermata per una ragione pratica: e' l'unica
 * parte della cronologia che ha una regola ("cambia il giorno") invece che un aspetto, e una
 * regola si prova senza accendere un telefono.
 *
 * Il fuso arriva da fuori: un test che dipende da dove sta la macchina che lo esegue non e' un
 * test.
 */
fun timelineOf(
  messages: List<ChatMessage>,
  zone: ZoneId = ZoneId.systemDefault(),
): List<TimelineRow> {
  if (messages.isEmpty()) return emptyList()
  val rows = ArrayList<TimelineRow>(messages.size + 4)
  var lastDay: LocalDate? = null
  for (message in messages) {
    val day = Instant.ofEpochMilli(message.createdAt).atZone(zone).toLocalDate()
    if (day != lastDay) {
      rows += TimelineRow.Day(day)
      lastDay = day
    }
    rows += TimelineRow.Message(message)
  }
  return rows
}
