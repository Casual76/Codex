package dev.pampa.codex.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Una chiave di conversazione messa via.
 *
 * Sta in una tabella sua e non in una colonna di `chats` per un motivo pratico e uno di sostanza.
 * Pratico: una riga di chat viene letta e ridisegnata di continuo, e non ha nessun bisogno di
 * portarsi dietro trentadue byte di segreto a ogni ricomposizione. Di sostanza: cancellare una
 * chiave e cancellare una conversazione sono due gesti diversi -- si puo' voler dimenticare come
 * si apriva una chat senza buttarne via i messaggi, che restano li', chiusi, come devono.
 *
 * Il contenuto e' **avvolto**: vedi `ChatKeyring`. Anche leggendo questa tabella non si ottiene
 * niente senza il vault aperto.
 */
@Entity(tableName = "chat_keys", primaryKeys = ["chatId", "epoch"])
data class KeyringEntity(
  val chatId: String,
  val wrapped: ByteArray,
  /**
   * L'epoca della chiave.
   *
   * Per una chat a due e' sempre zero: rifare il rito con una parola diversa **sostituisce** la
   * chiave, perche' i messaggi vecchi si aprivano con quella e continueranno a farlo solo se la
   * parola era la stessa -- non c'e' niente da conservare.
   *
   * Per un gruppo no: ogni volta che qualcuno esce nasce un'epoca nuova, e le vecchie **restano**.
   * Buttarle via renderebbe illeggibile a chi c'era tutto quello che si erano detti prima, che e'
   * l'esatto contrario di quello che una rotazione di chiave deve ottenere. Per questo la chiave
   * primaria e' la coppia: una riga per epoca.
   */
  val epoch: Int = 0,
  val storedAt: Long = 0,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is KeyringEntity) return false
    return chatId == other.chatId &&
      wrapped.contentEquals(other.wrapped) &&
      epoch == other.epoch &&
      storedAt == other.storedAt
  }

  override fun hashCode(): Int {
    var result = chatId.hashCode()
    result = 31 * result + wrapped.contentHashCode()
    result = 31 * result + epoch
    return result
  }
}

@Dao
interface KeyringDao {

  /** L'ultima epoca conosciuta di una conversazione: e' quella con cui si scrive adesso. */
  @Query("SELECT * FROM chat_keys WHERE chatId = :chatId ORDER BY epoch DESC LIMIT 1")
  suspend fun byChatId(chatId: String): KeyringEntity?

  /** Una precisa: serve ad aprire un messaggio vecchio, che dice nella busta con quale e' chiuso. */
  @Query("SELECT * FROM chat_keys WHERE chatId = :chatId AND epoch = :epoch")
  suspend fun byEpoch(chatId: String, epoch: Int): KeyringEntity?

  /**
   * Se una chiave c'e', senza tirarla fuori.
   *
   * Serve alla lista dei contatti, che deve solo dire "il rito e' stato fatto": scartare
   * l'involucro per rispondere a questa domanda vorrebbe dire portare in memoria un segreto che
   * nessuno usera', per ogni contatto, a ogni ridisegno.
   */
  @Query("SELECT EXISTS(SELECT 1 FROM chat_keys WHERE chatId = :chatId)")
  suspend fun exists(chatId: String): Boolean

  @Upsert
  suspend fun upsert(entry: KeyringEntity)

  /** Tutte le epoche di una conversazione, dalla piu' recente. */
  @Query("SELECT * FROM chat_keys WHERE chatId = :chatId ORDER BY epoch DESC")
  suspend fun allForChat(chatId: String): List<KeyringEntity>

  @Query("DELETE FROM chat_keys WHERE chatId = :chatId")
  suspend fun delete(chatId: String)
}
