package dev.pampa.codex.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Una storia: un sigillo che dura un giorno.
 *
 * E' un messaggio senza destinatario, e infatti e' costruita allo stesso modo -- busta CDX3,
 * tecnica e seme decisi dall'id -- perche' quello che cambia rispetto a un messaggio non e' come e'
 * chiusa, e' **a chi e' rivolta e quanto vive**.
 *
 * `expiresAt` sta nella riga e non si calcola all'occorrenza: chi guarda deve vedere la stessa
 * scadenza anche se il telefono cambia fuso o l'app resta aperta oltre la mezzanotte.
 */
@Entity(tableName = "stories", indices = [Index("expiresAt")])
data class StoryEntity(
  @PrimaryKey val id: String,
  /** Il Codex ID di chi l'ha pubblicata. */
  val authorId: String,
  val mine: Boolean,
  val envelope: ByteArray,
  val technique: String,
  val seed: Long,
  val paintingId: String? = null,
  val createdAt: Long,
  val expiresAt: Long,
  /** Quando e' stata aperta la prima volta. `null` = mai vista. */
  val seenAt: Long? = null,
  /**
   * La chiave di questa storia, avvolta come quelle delle conversazioni.
   *
   * `null` per le proprie: quella si ricalcola dal seme dell'identita' e non ha bisogno di essere
   * conservata. Per una storia ricevuta invece la chiave e' nata sul telefono di qualcun altro, e
   * senza tenerla la storia si aprirebbe una volta sola -- finche' l'app resta aperta -- per poi
   * diventare illeggibile.
   */
  val wrappedKey: ByteArray? = null,
  /** L'account di chi l'ha scritta: serve a dirgli "l'ho vista". Vuoto per le proprie. */
  val authorUid: String = "",
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is StoryEntity) return false
    return id == other.id &&
      authorId == other.authorId &&
      mine == other.mine &&
      envelope.contentEquals(other.envelope) &&
      technique == other.technique &&
      seed == other.seed &&
      paintingId == other.paintingId &&
      createdAt == other.createdAt &&
      expiresAt == other.expiresAt &&
      seenAt == other.seenAt &&
      authorUid == other.authorUid &&
      (wrappedKey?.toList() == other.wrappedKey?.toList())
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + authorId.hashCode()
    result = 31 * result + envelope.contentHashCode()
    result = 31 * result + createdAt.hashCode()
    result = 31 * result + expiresAt.hashCode()
    result = 31 * result + (seenAt?.hashCode() ?: 0)
    return result
  }
}

@Dao
interface StoryDao {

  /**
   * Le storie ancora valide, la piu' recente per prima.
   *
   * La scadenza si filtra **nella query**: una storia scaduta che resta in lista finche' qualcuno
   * fa pulizia sarebbe una storia che dura piu' di un giorno, e la durata e' tutto quello che una
   * storia e'.
   */
  @Query("SELECT * FROM stories WHERE expiresAt > :now ORDER BY createdAt DESC")
  fun observeLive(now: Long): Flow<List<StoryEntity>>

  @Query("SELECT * FROM stories WHERE id = :id")
  suspend fun byId(id: String): StoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(story: StoryEntity)

  @Query("UPDATE stories SET seenAt = :at WHERE id = :id AND seenAt IS NULL")
  suspend fun markSeen(id: String, at: Long)

  @Query("DELETE FROM stories WHERE id = :id")
  suspend fun delete(id: String)

  /** La pulizia vera: toglie da disco quelle che nessuno vedra' piu'. */
  @Query("DELETE FROM stories WHERE expiresAt <= :now")
  suspend fun deleteExpired(now: Long)
}
