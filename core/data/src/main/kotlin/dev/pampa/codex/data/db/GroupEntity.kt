package dev.pampa.codex.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Chi c'e' dentro un gruppo, su questo telefono.
 *
 * La lista dei membri **non e' un dato del server**: il server sa gli uid perche' deve consegnare,
 * ma i nomi e i Codex ID arrivano dentro la busta cifrata che porta la chiave. Se stesse solo
 * online, un gruppo aperto in aereo non saprebbe nemmeno chi lo compone.
 *
 * `uid` puo' essere vuoto: qualcuno puo' essere nel gruppo prima che noi lo si conosca come account
 * -- succede a chi entra da un invito e non ha ancora scambiato la scheda. Il messaggio si consegna
 * lo stesso, perche' a consegnare pensa il gruppo, non i singoli.
 */
@Entity(tableName = "group_members", primaryKeys = ["chatId", "codexId"])
data class GroupMemberEntity(
  val chatId: String,
  val codexId: String,
  val uid: String = "",
  val name: String,
  val admin: Boolean = false,
  val joinedAt: Long = 0,
  /**
   * Se ne e' andato, o e' stato tolto.
   *
   * Resta nella tabella invece di sparire: i suoi messaggi vecchi sono ancora nella conversazione,
   * e senza questa riga comparirebbero firmati da un Codex ID senza nome.
   */
  val gone: Boolean = false,
)

@Dao
interface GroupMemberDao {

  @Query("SELECT * FROM group_members WHERE chatId = :chatId ORDER BY admin DESC, name COLLATE NOCASE ASC")
  fun observeForChat(chatId: String): Flow<List<GroupMemberEntity>>

  @Query("SELECT * FROM group_members WHERE chatId = :chatId")
  suspend fun forChat(chatId: String): List<GroupMemberEntity>

  @Query("SELECT * FROM group_members WHERE chatId = :chatId AND codexId = :codexId")
  suspend fun byCodexId(chatId: String, codexId: String): GroupMemberEntity?

  @Upsert
  suspend fun upsert(member: GroupMemberEntity)

  @Upsert
  suspend fun upsertAll(members: List<GroupMemberEntity>)

  @Query("UPDATE group_members SET gone = 1 WHERE chatId = :chatId AND codexId = :codexId")
  suspend fun markGone(chatId: String, codexId: String)

  @Query("DELETE FROM group_members WHERE chatId = :chatId")
  suspend fun deleteForChat(chatId: String)
}
