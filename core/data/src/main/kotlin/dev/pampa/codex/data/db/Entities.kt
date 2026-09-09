package dev.pampa.codex.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Che tipo di conversazione e': cambia chi sono i partecipanti e da dove viene la chiave. */
object ChatKind {
  /** Le note a se stessi: una chat vera, con una chiave vera, e un solo partecipante. */
  const val SELF = "self"

  /** Uno a uno con un contatto. */
  const val DIRECT = "direct"

  /** Un gruppo. */
  const val GROUP = "group"
}

/** Dove sta un messaggio nel suo viaggio. */
object MessageStatus {
  const val PENDING = "pending"
  const val SENT = "sent"
  const val DELIVERED = "delivered"
  const val READ = "read"
  const val FAILED = "failed"
}

@Entity(tableName = "chats")
data class ChatEntity(
  @PrimaryKey val id: String,
  val kind: String,
  /** Il nome mostrato. Per una chat diretta e' il nome del contatto, copiato qui per la lista. */
  val title: String,
  /** Il Codex ID dell'altra persona, quando c'e'. */
  val peerId: String? = null,
  /** Il seme dell'emblema: nasce dalla chiave di chat, e i due lati lo vedono uguale. */
  val emblemSeed: Long = 0,
  val keyEpoch: Int = 0,
  val createdAt: Long = 0,
  val lastActivityAt: Long = 0,
  /**
   * Cosa dire nella lista senza dire cosa c'e' scritto: "una roccia", "delle rune".
   * L'anteprima di una chat non mostra mai il testo — sarebbe il modo piu' rapido di svuotare di
   * senso tutto il resto.
   */
  val lastSealTechnique: String? = null,
  val lastFromMe: Boolean = false,
  val unread: Int = 0,
  val pinned: Boolean = false,
  val muted: Boolean = false,
  /** Secondi dopo i quali un messaggio rivelato sparisce; 0 = mai. */
  val ttlSeconds: Int = 0,
  /**
   * Fin dove **io** ho ricevuto e letto, in questa conversazione.
   *
   * Stanno qui e non solo sul server per una ragione precisa: una ricevuta che vive solo online si
   * perde se il telefono era senza rete quando la conversazione e' stata letta. Cosi' invece il
   * fatto e' registrato subito in casa, e il motore lo porta fuori quando puo'.
   */
  val myDeliveredAt: Long = 0,
  val myReadAt: Long = 0,
  /**
   * Questa conversazione si apre solo dopo aver dimostrato di essere chi si dice.
   *
   * E' una serratura **dentro** l'app, e serve a una cosa sola: il telefono passato di mano. "Guarda
   * che foto" e poi lo schermo resta acceso in mano a un altro. Non protegge da chi ha il telefono
   * per un'ora -- per quello c'e' il vault -- protegge dai trenta secondi in cui non ce l'hai tu.
   */
  val locked: Boolean = false,
)

@Entity(
  tableName = "messages",
  indices = [Index("chatId", "createdAt")],
)
data class MessageEntity(
  @PrimaryKey val id: String,
  val chatId: String,
  val senderId: String,
  val outgoing: Boolean,
  /** La busta CDX3: l'unica forma in cui il testo esiste su disco. */
  val envelope: ByteArray,
  /** Tecnica e seme del sigillo, gia' risolti: la bolla chiusa si disegna senza aprire niente. */
  val technique: String,
  val seed: Long,
  val paintingId: String? = null,
  val createdAt: Long,
  val status: String = MessageStatus.PENDING,
  /**
   * Se e' gia' stato aperto almeno una volta.
   *
   * E' la differenza fra "Rivela tutto", che riapre solo questi, e un messaggio mai visto, che il
   * rituale se lo merita per intero.
   */
  val revealedOnce: Boolean = false,
  val viewOnce: Boolean = false,
  /** Un "visualizza una volta" gia' visto: resta come segnaposto, senza contenuto. */
  val burned: Boolean = false,
  val expiresAt: Long? = null,
  val replyTo: String? = null,
) {
  // `ByteArray` non ha uguaglianza per valore: senza questi due, Room e le liste di Compose
  // considererebbero diversi due messaggi identici a ogni ricomposizione.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MessageEntity) return false
    return id == other.id &&
      chatId == other.chatId &&
      senderId == other.senderId &&
      outgoing == other.outgoing &&
      envelope.contentEquals(other.envelope) &&
      technique == other.technique &&
      seed == other.seed &&
      paintingId == other.paintingId &&
      createdAt == other.createdAt &&
      status == other.status &&
      revealedOnce == other.revealedOnce &&
      viewOnce == other.viewOnce &&
      burned == other.burned &&
      expiresAt == other.expiresAt &&
      replyTo == other.replyTo
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + chatId.hashCode()
    result = 31 * result + envelope.contentHashCode()
    result = 31 * result + createdAt.hashCode()
    result = 31 * result + status.hashCode()
    result = 31 * result + revealedOnce.hashCode()
    result = 31 * result + burned.hashCode()
    return result
  }
}

@Entity(tableName = "contacts")
data class ContactEntity(
  @PrimaryKey val codexId: String,
  val uid: String = "",
  val name: String,
  val avatarSeed: Long,
  val x25519Public: ByteArray,
  val ed25519Public: ByteArray,
  val addedAt: Long,
  val deletedByPeer: Boolean = false,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ContactEntity) return false
    return codexId == other.codexId &&
      uid == other.uid &&
      name == other.name &&
      avatarSeed == other.avatarSeed &&
      x25519Public.contentEquals(other.x25519Public) &&
      ed25519Public.contentEquals(other.ed25519Public) &&
      addedAt == other.addedAt &&
      deletedByPeer == other.deletedByPeer
  }

  override fun hashCode(): Int {
    var result = codexId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + x25519Public.contentHashCode()
    return result
  }
}
