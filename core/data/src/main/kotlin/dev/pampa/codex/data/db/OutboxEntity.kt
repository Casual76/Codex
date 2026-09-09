package dev.pampa.codex.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Un messaggio scritto e non ancora consegnato.
 *
 * **Perche' una tabella e non una coda in memoria.** Chi scrive un messaggio in metropolitana lo
 * scrive lo stesso: il messaggio esiste, sta nella conversazione, e ha la sua spunta di attesa.
 * Quello che manca e' solo la consegna. Se la coda vivesse in memoria, chiudere l'app -- o il
 * sistema che la chiude per fare spazio -- lo cancellerebbe senza dire niente a nessuno, e sarebbe
 * il difetto peggiore che un'app di messaggistica possa avere: perdere qualcosa in silenzio.
 *
 * Qui non c'e' nessun contenuto, solo l'id: la busta sta gia' in `messages`, e duplicarla vorrebbe
 * dire tenerne due copie da cancellare insieme.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
  @PrimaryKey val messageId: String,
  val chatId: String,
  val attempts: Int = 0,
  val lastAttemptAt: Long = 0,
  val queuedAt: Long = 0,
)

@Dao
interface OutboxDao {

  /** Cosa c'e' da mandare, in ordine di scrittura: le conversazioni non si scompaginano. */
  @Query("SELECT * FROM outbox ORDER BY queuedAt ASC")
  fun observeAll(): Flow<List<OutboxEntity>>

  @Query("SELECT * FROM outbox ORDER BY queuedAt ASC LIMIT :limit")
  suspend fun pending(limit: Int = 50): List<OutboxEntity>

  @Upsert
  suspend fun upsert(entry: OutboxEntity)

  @Query("UPDATE outbox SET attempts = attempts + 1, lastAttemptAt = :at WHERE messageId = :messageId")
  suspend fun failed(messageId: String, at: Long)

  @Query("DELETE FROM outbox WHERE messageId = :messageId")
  suspend fun delete(messageId: String)

  @Query("DELETE FROM outbox WHERE chatId = :chatId")
  suspend fun deleteForChat(chatId: String)
}
