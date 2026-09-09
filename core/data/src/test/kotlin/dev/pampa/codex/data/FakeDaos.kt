package dev.pampa.codex.data

import dev.pampa.codex.crypto.CodexIdentity
import dev.pampa.codex.crypto.ContactCard
import dev.pampa.codex.crypto.SignedContactCard
import dev.pampa.codex.data.db.ChatDao
import dev.pampa.codex.data.db.ChatEntity
import dev.pampa.codex.data.db.ContactDao
import dev.pampa.codex.data.db.ContactEntity
import dev.pampa.codex.data.db.GroupMemberDao
import dev.pampa.codex.data.db.GroupMemberEntity
import dev.pampa.codex.data.db.KeyringDao
import dev.pampa.codex.data.db.KeyringEntity
import dev.pampa.codex.data.db.MessageDao
import dev.pampa.codex.data.db.MessageEntity
import dev.pampa.codex.data.db.MessageStatus
import dev.pampa.codex.data.db.OutboxDao
import dev.pampa.codex.data.db.OutboxEntity
import dev.pampa.codex.data.identity.CodexIdentitySource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Il magazzino di un telefono, tenuto in memoria.
 *
 * Room non gira su una JVM da sola, ma i repository di Codex non parlano con Room: parlano con
 * quattro interfacce piccole. Riempirle qui costa una pagina e in cambio si puo' provare **il
 * pairing fra due dispositivi** senza dispositivi -- che e' l'unica cosa di M3 che, senza test,
 * si scoprirebbe rotta soltanto con due telefoni in mano e due persone che si parlano.
 */
class FakeDatabase {
  val chats = FakeChatDao()
  val messages = FakeMessageDao()
  val contacts = FakeContactDao()
  val keyring = FakeKeyringDao()
  val outbox = FakeOutboxDao()
  val groupMembers = FakeGroupMemberDao()
}

class FakeGroupMemberDao : GroupMemberDao {
  val rows = MutableStateFlow<Map<Pair<String, String>, GroupMemberEntity>>(emptyMap())

  override fun observeForChat(chatId: String): Flow<List<GroupMemberEntity>> =
    rows.map { all -> all.values.filter { it.chatId == chatId }.sortedBy { it.name } }

  override suspend fun forChat(chatId: String): List<GroupMemberEntity> =
    rows.value.values.filter { it.chatId == chatId }

  override suspend fun byCodexId(chatId: String, codexId: String): GroupMemberEntity? =
    rows.value[chatId to codexId]

  override suspend fun upsert(member: GroupMemberEntity) {
    rows.value = rows.value + ((member.chatId to member.codexId) to member)
  }

  override suspend fun upsertAll(members: List<GroupMemberEntity>) {
    rows.value = rows.value + members.associateBy { it.chatId to it.codexId }
  }

  override suspend fun markGone(chatId: String, codexId: String) {
    val current = rows.value[chatId to codexId] ?: return
    rows.value = rows.value + ((chatId to codexId) to current.copy(gone = true))
  }

  override suspend fun deleteForChat(chatId: String) {
    rows.value = rows.value.filterValues { it.chatId != chatId }
  }
}

class FakeOutboxDao : OutboxDao {
  val rows = MutableStateFlow<Map<String, OutboxEntity>>(emptyMap())

  override fun observeAll(): Flow<List<OutboxEntity>> =
    rows.map { it.values.sortedBy { entry -> entry.queuedAt } }

  override suspend fun pending(limit: Int): List<OutboxEntity> =
    rows.value.values.sortedBy { it.queuedAt }.take(limit)

  override suspend fun upsert(entry: OutboxEntity) {
    rows.value = rows.value + (entry.messageId to entry)
  }

  override suspend fun failed(messageId: String, at: Long) {
    val current = rows.value[messageId] ?: return
    rows.value = rows.value + (messageId to current.copy(attempts = current.attempts + 1, lastAttemptAt = at))
  }

  override suspend fun delete(messageId: String) {
    rows.value = rows.value - messageId
  }

  override suspend fun deleteForChat(chatId: String) {
    rows.value = rows.value.filterValues { it.chatId != chatId }
  }
}

class FakeChatDao : ChatDao {
  val rows = MutableStateFlow<Map<String, ChatEntity>>(emptyMap())

  override fun observeAll(): Flow<List<ChatEntity>> =
    rows.map { it.values.sortedByDescending { chat -> chat.lastActivityAt } }

  override fun observe(id: String): Flow<ChatEntity?> = rows.map { it[id] }

  override suspend fun byId(id: String): ChatEntity? = rows.value[id]

  override suspend fun firstOfKind(kind: String): ChatEntity? =
    rows.value.values.firstOrNull { it.kind == kind }

  override suspend fun upsert(chat: ChatEntity) {
    rows.value = rows.value + (chat.id to chat)
  }

  override suspend fun touch(id: String, at: Long, technique: String, fromMe: Boolean) {
    update(id) { it.copy(lastActivityAt = at, lastSealTechnique = technique, lastFromMe = fromMe) }
  }

  override suspend fun clearUnread(id: String) = update(id) { it.copy(unread = 0) }

  override suspend fun setDelivered(id: String, at: Long) =
    update(id) { if (it.myDeliveredAt < at) it.copy(myDeliveredAt = at) else it }

  override suspend fun setRead(id: String, at: Long) =
    update(id) { if (it.myReadAt < at) it.copy(myReadAt = at) else it }

  override suspend fun reseal(id: String, title: String, emblemSeed: Long) =
    update(id) { it.copy(title = title, emblemSeed = emblemSeed) }

  override suspend fun setKeyEpoch(id: String, epoch: Int) = update(id) { it.copy(keyEpoch = epoch) }

  override suspend fun setLocked(id: String, locked: Boolean) = update(id) { it.copy(locked = locked) }

  override suspend fun setTtl(id: String, seconds: Int) = update(id) { it.copy(ttlSeconds = seconds) }

  override suspend fun delete(id: String) {
    rows.value = rows.value - id
  }

  private inline fun update(id: String, change: (ChatEntity) -> ChatEntity) {
    val current = rows.value[id] ?: return
    rows.value = rows.value + (id to change(current))
  }
}

class FakeMessageDao : MessageDao {
  val rows = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())

  override fun observeForChat(chatId: String, limit: Int): Flow<List<MessageEntity>> =
    rows.map { all ->
      all.values.filter { it.chatId == chatId }.sortedByDescending { it.createdAt }.take(limit)
    }

  override suspend fun byId(id: String): MessageEntity? = rows.value[id]

  override suspend fun insert(message: MessageEntity): Long {
    if (rows.value.containsKey(message.id)) return -1
    rows.value = rows.value + (message.id to message)
    return 1
  }

  override suspend fun markRevealed(id: String) = update(id) { it.copy(revealedOnce = true) }

  override suspend fun setExpiry(id: String, at: Long) = update(id) {
    if (it.expiresAt == null) it.copy(expiresAt = at) else it
  }

  override suspend fun setStatus(id: String, status: String) = update(id) { it.copy(status = status) }

  override suspend fun advanceToSent(id: String) = update(id) {
    if (it.status in setOf(MessageStatus.PENDING, MessageStatus.FAILED)) {
      it.copy(status = MessageStatus.SENT)
    } else {
      it
    }
  }

  override suspend fun markDeliveredUpTo(chatId: String, at: Long) {
    rows.value = rows.value.mapValues { (_, m) ->
      if (m.chatId == chatId && m.outgoing && m.createdAt <= at &&
        m.status in setOf(MessageStatus.PENDING, MessageStatus.SENT)
      ) {
        m.copy(status = MessageStatus.DELIVERED)
      } else {
        m
      }
    }
  }

  override suspend fun markReadUpTo(chatId: String, at: Long) {
    rows.value = rows.value.mapValues { (_, m) ->
      if (m.chatId == chatId && m.outgoing && m.createdAt <= at &&
        m.status in setOf(MessageStatus.PENDING, MessageStatus.SENT, MessageStatus.DELIVERED)
      ) {
        m.copy(status = MessageStatus.READ)
      } else {
        m
      }
    }
  }

  override suspend fun burn(id: String) = update(id) { it.copy(burned = true, envelope = ByteArray(0)) }

  override suspend fun delete(id: String) {
    rows.value = rows.value - id
  }

  override suspend fun idsForChat(chatId: String): List<String> =
    rows.value.values.filter { it.chatId == chatId }.map { it.id }

  override suspend fun deleteForChat(chatId: String) {
    rows.value = rows.value.filterValues { it.chatId != chatId }
  }

  override suspend fun expired(now: Long): List<MessageEntity> =
    rows.value.values.filter { it.expiresAt != null && it.expiresAt!! <= now }

  override suspend fun deleteExpired(now: Long) {
    rows.value = rows.value.filterValues { it.expiresAt == null || it.expiresAt!! > now }
  }

  private inline fun update(id: String, change: (MessageEntity) -> MessageEntity) {
    val current = rows.value[id] ?: return
    rows.value = rows.value + (id to change(current))
  }
}

class FakeContactDao : ContactDao {
  val rows = MutableStateFlow<Map<String, ContactEntity>>(emptyMap())

  override fun observeAll(): Flow<List<ContactEntity>> =
    rows.map { it.values.sortedBy { contact -> contact.name.lowercase() } }

  override suspend fun byCodexId(codexId: String): ContactEntity? = rows.value[codexId]

  override suspend fun byUid(uid: String): ContactEntity? =
    rows.value.values.firstOrNull { it.uid == uid && it.uid.isNotBlank() }

  override suspend fun upsert(contact: ContactEntity) {
    rows.value = rows.value + (contact.codexId to contact)
  }

  override suspend fun delete(codexId: String) {
    rows.value = rows.value - codexId
  }
}

class FakeKeyringDao : KeyringDao {
  /** Una riga per (conversazione, epoca), come nel database vero. */
  val rows = MutableStateFlow<Map<Pair<String, Int>, KeyringEntity>>(emptyMap())

  override suspend fun byChatId(chatId: String): KeyringEntity? =
    rows.value.values.filter { it.chatId == chatId }.maxByOrNull { it.epoch }

  override suspend fun byEpoch(chatId: String, epoch: Int): KeyringEntity? =
    rows.value[chatId to epoch]

  override suspend fun allForChat(chatId: String): List<KeyringEntity> =
    rows.value.values.filter { it.chatId == chatId }.sortedByDescending { it.epoch }

  override suspend fun exists(chatId: String): Boolean =
    rows.value.keys.any { it.first == chatId }

  override suspend fun upsert(entry: KeyringEntity) {
    rows.value = rows.value + ((entry.chatId to entry.epoch) to entry)
  }

  override suspend fun delete(chatId: String) {
    rows.value = rows.value.filterKeys { it.first != chatId }
  }
}

/**
 * Un'identita' aperta, senza vault e senza Argon2id.
 *
 * Il vault ha i suoi test; qui interessa solo che ci sia qualcuno con delle chiavi. [locked]
 * permette di provare cosa succede quando la serratura si chiude, che e' la meta' della promessa.
 */
class FakeIdentitySource(
  private val identity: CodexIdentity,
  val name: String = "Prova",
  val avatarSeed: Long = 7L,
) : CodexIdentitySource {

  var locked: Boolean = false

  override fun identityOrNull(): CodexIdentity? = identity.takeIf { !locked }

  override fun requireIdentity(): CodexIdentity =
    identityOrNull() ?: error("identita' non disponibile")

  override suspend fun signedCard(uid: String): SignedContactCard? =
    identityOrNull()?.let { ContactCard.of(it, name = name, avatarSeed = avatarSeed, uid = uid) }

  /** Aperta finche' non si chiude la serratura: nei test la si muove a mano con [locked]. */
  override val unlocked: Flow<Boolean> = MutableStateFlow(true)
}
