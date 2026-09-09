package dev.pampa.codex.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

  /** Le chat come si vedono nella lista: fissate in cima, poi le piu' recenti. */
  @Query("SELECT * FROM chats ORDER BY pinned DESC, lastActivityAt DESC")
  fun observeAll(): Flow<List<ChatEntity>>

  @Query("SELECT * FROM chats WHERE id = :id")
  fun observe(id: String): Flow<ChatEntity?>

  @Query("SELECT * FROM chats WHERE id = :id")
  suspend fun byId(id: String): ChatEntity?

  @Query("SELECT * FROM chats WHERE kind = :kind LIMIT 1")
  suspend fun firstOfKind(kind: String): ChatEntity?

  @Upsert
  suspend fun upsert(chat: ChatEntity)

  @Query(
    """
    UPDATE chats
    SET lastActivityAt = :at, lastSealTechnique = :technique, lastFromMe = :fromMe
    WHERE id = :id
    """,
  )
  suspend fun touch(id: String, at: Long, technique: String, fromMe: Boolean)

  @Query("UPDATE chats SET unread = 0 WHERE id = :id")
  suspend fun clearUnread(id: String)

  /** Fin dove ho ricevuto: non torna mai indietro, altrimenti una ricevuta si "disfarebbe". */
  @Query("UPDATE chats SET myDeliveredAt = :at WHERE id = :id AND myDeliveredAt < :at")
  suspend fun setDelivered(id: String, at: Long)

  @Query("UPDATE chats SET myReadAt = :at WHERE id = :id AND myReadAt < :at")
  suspend fun setRead(id: String, at: Long)

  /**
   * Rifare il rito su una conversazione che esiste gia'.
   *
   * Cambiano il nome e l'emblema, che sono le due cose che dipendono dal contatto e dalla chiave.
   * Tutto il resto -- fissata, silenziata, scadenza, non letti, quando e' nata -- resta com'era: un
   * `@Upsert` qui sostituirebbe l'intera riga e azzererebbe le scelte di chi la usa, per il solo
   * fatto di aver ripetuto una parola.
   */
  @Query("UPDATE chats SET title = :title, emblemSeed = :emblemSeed WHERE id = :id")
  suspend fun reseal(id: String, title: String, emblemSeed: Long)

  /** L'epoca corrente della chiave: cambia quando qualcuno esce da un gruppo. */
  @Query("UPDATE chats SET keyEpoch = :epoch WHERE id = :id")
  suspend fun setKeyEpoch(id: String, epoch: Int)

  @Query("UPDATE chats SET locked = :locked WHERE id = :id")
  suspend fun setLocked(id: String, locked: Boolean)

  @Query("UPDATE chats SET ttlSeconds = :seconds WHERE id = :id")
  suspend fun setTtl(id: String, seconds: Int)

  @Query("DELETE FROM chats WHERE id = :id")
  suspend fun delete(id: String)
}

@Dao
interface MessageDao {

  /**
   * Gli ultimi `limit` messaggi di una conversazione, dal piu' recente.
   *
   * Si prendono dal fondo e non dall'inizio: una chat si apre dove si era rimasti, non nel 2019. Chi
   * chiama li rigira nel verso della lettura -- rigirare ottanta righe costa niente, tenerne in
   * memoria diecimila costa a ogni ridisegno.
   */
  @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT :limit")
  fun observeForChat(chatId: String, limit: Int): Flow<List<MessageEntity>>

  @Query("SELECT * FROM messages WHERE id = :id")
  suspend fun byId(id: String): MessageEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insert(message: MessageEntity): Long

  @Query("UPDATE messages SET revealedOnce = 1 WHERE id = :id")
  suspend fun markRevealed(id: String)

  /**
   * Quando questo messaggio smettera' di esistere.
   *
   * Si scrive **all'apertura**, non all'invio: una scadenza che parte da quando e' stato scritto
   * farebbe sparire un messaggio mai letto, che e' il contrario di quello che serve.
   */
  @Query("UPDATE messages SET expiresAt = :at WHERE id = :id AND expiresAt IS NULL")
  suspend fun setExpiry(id: String, at: Long)

  @Query("UPDATE messages SET status = :status WHERE id = :id")
  suspend fun setStatus(id: String, status: String)

  /**
   * "Spedito", ma solo se non era gia' piu' avanti.
   *
   * Un messaggio consegnato da vicino e' gia' a "consegnato" quando piu' tardi passa dal server:
   * riscriverlo a "spedito" lo farebbe tornare indietro sotto gli occhi di chi lo ha scritto, e una
   * spunta che indietreggia toglie fiducia a tutte le altre.
   */
  @Query("UPDATE messages SET status = 'sent' WHERE id = :id AND status IN ('pending', 'failed')")
  suspend fun advanceToSent(id: String)

  /**
   * Le spunte di una conversazione, fino a un certo momento.
   *
   * Solo **in avanti**: uno stato non torna mai indietro. Una ricevuta che arriva in ritardo e
   * riportasse "letto" a "consegnato" farebbe lampeggiare la spunta senza motivo, e chi guarda si
   * fiderebbe meno di tutte le altre.
   */
  @Query(
    """
    UPDATE messages SET status = 'delivered'
    WHERE chatId = :chatId AND outgoing = 1 AND createdAt <= :at
      AND status IN ('pending', 'sent')
    """,
  )
  suspend fun markDeliveredUpTo(chatId: String, at: Long)

  @Query(
    """
    UPDATE messages SET status = 'read'
    WHERE chatId = :chatId AND outgoing = 1 AND createdAt <= :at
      AND status IN ('pending', 'sent', 'delivered')
    """,
  )
  suspend fun markReadUpTo(chatId: String, at: Long)

  /**
   * Un "visualizza una volta" che si e' consumato.
   *
   * La busta viene sostituita con un array vuoto invece di essere cancellata insieme alla riga: il
   * messaggio resta nella conversazione come segnaposto, e chi guarda vede che qualcosa c'e' stato.
   */
  @Query("UPDATE messages SET burned = 1, envelope = x'' WHERE id = :id")
  suspend fun burn(id: String)

  @Query("DELETE FROM messages WHERE id = :id")
  suspend fun delete(id: String)

  /**
   * Gli identificatori dei messaggi di una conversazione.
   *
   * Serve a chi cancella: un messaggio puo' avere un **file** accanto, e quel file non sta nel
   * database. Cancellare le righe senza sapere quali erano lascerebbe nella cartella dei media
   * degli allegati che nessuno puo' piu' nominare -- e che, finche' la chiave resta, si aprono
   * ancora.
   */
  @Query("SELECT id FROM messages WHERE chatId = :chatId")
  suspend fun idsForChat(chatId: String): List<String>

  @Query("DELETE FROM messages WHERE chatId = :chatId")
  suspend fun deleteForChat(chatId: String)

  /** I messaggi scaduti: li cerca chi fa pulizia, non la schermata. */
  @Query("SELECT * FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
  suspend fun expired(now: Long): List<MessageEntity>

  @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt <= :now")
  suspend fun deleteExpired(now: Long)
}

@Dao
interface ContactDao {

  @Query("SELECT * FROM contacts ORDER BY name COLLATE NOCASE ASC")
  fun observeAll(): Flow<List<ContactEntity>>

  @Query("SELECT * FROM contacts WHERE codexId = :codexId")
  suspend fun byCodexId(codexId: String): ContactEntity?

  /**
   * Chi ha questo account.
   *
   * La posta arriva con l'uid di chi ha scritto, non con il suo Codex ID: e' l'unico nome che il
   * server conosce. Per aprire la busta serve la sua chiave pubblica, e per trovarla si parte da li'.
   */
  @Query("SELECT * FROM contacts WHERE uid = :uid AND uid != '' LIMIT 1")
  suspend fun byUid(uid: String): ContactEntity?

  @Upsert
  suspend fun upsert(contact: ContactEntity)

  @Query("DELETE FROM contacts WHERE codexId = :codexId")
  suspend fun delete(codexId: String)
}
