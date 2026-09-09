package dev.pampa.codex.ui.chat

import dev.pampa.codex.data.chat.ChatMessage
import dev.pampa.codex.model.SealSpec
import dev.pampa.codex.model.Technique
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimelineTest {

  private val rome: ZoneId = ZoneId.of("Europe/Rome")

  private fun messageAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): ChatMessage {
    val at = LocalDateTime.of(year, month, day, hour, minute).atZone(rome).toInstant().toEpochMilli()
    return ChatMessage(
      id = "$year-$month-$day-$hour-$minute",
      outgoing = true,
      text = "",
      spec = SealSpec(technique = Technique.RUNE, seed = 0, paintingId = null),
      createdAt = at,
      status = "read",
      revealedOnce = true,
      viewOnce = false,
      burned = false,
    )
  }

  @Test
  fun `una conversazione vuota non ha righe`() {
    assertTrue(timelineOf(emptyList(), rome).isEmpty())
  }

  @Test
  fun `il primo messaggio porta sempre il suo giorno`() {
    val rows = timelineOf(listOf(messageAt(2026, 9, 7, 12, 0)), rome)
    assertEquals(2, rows.size)
    assertEquals(LocalDate.of(2026, 9, 7), (rows[0] as TimelineRow.Day).date)
  }

  @Test
  fun `messaggi dello stesso giorno hanno un separatore solo`() {
    val rows = timelineOf(
      listOf(
        messageAt(2026, 9, 7, 9, 0),
        messageAt(2026, 9, 7, 13, 30),
        messageAt(2026, 9, 7, 23, 59),
      ),
      rome,
    )
    assertEquals(1, rows.count { it is TimelineRow.Day })
    assertEquals(3, rows.count { it is TimelineRow.Message })
  }

  @Test
  fun `un separatore per ogni cambio di giorno`() {
    val rows = timelineOf(
      listOf(
        messageAt(2026, 9, 6, 23, 50),
        messageAt(2026, 9, 7, 0, 10),
        messageAt(2026, 9, 9, 8, 0),
      ),
      rome,
    )
    val days = rows.filterIsInstance<TimelineRow.Day>().map { it.date }
    assertEquals(
      listOf(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 9)),
      days,
    )
  }

  @Test
  fun `il giorno e' quello del fuso, non quello UTC`() {
    // Le 00:30 di Roma sono ancora il giorno prima a Londra: senza il fuso giusto il separatore
    // comparirebbe a meta' serata.
    val message = messageAt(2026, 9, 7, 0, 30)
    val aRoma = timelineOf(listOf(message), rome).first() as TimelineRow.Day
    val aLondra = timelineOf(listOf(message), ZoneId.of("Europe/London")).first() as TimelineRow.Day
    assertEquals(LocalDate.of(2026, 9, 7), aRoma.date)
    assertEquals(LocalDate.of(2026, 9, 6), aLondra.date)
  }

  @Test
  fun `ogni riga ha una chiave sua`() {
    val rows = timelineOf(
      listOf(messageAt(2026, 9, 6, 10, 0), messageAt(2026, 9, 7, 10, 0)),
      rome,
    )
    assertEquals(rows.size, rows.map { it.key }.toSet().size)
  }
}
