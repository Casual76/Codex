package dev.pampa.codex.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Il database locale di Codex, cifrato a riposo.
 *
 * Cosa contiene e cosa no: contiene le **buste** dei messaggi, non i messaggi. Anche a database
 * aperto, senza la chiave della chat non si legge niente. La cifratura del file e' un secondo
 * strato: protegge i metadati -- chi, quando, quante volte -- che una busta non nasconde.
 *
 * La chiave del file sta nell'Android Keystore e **non richiede l'impronta**: le notifiche in
 * arrivo devono poter scrivere un messaggio con l'app chiusa e lo schermo bloccato. Quello che
 * l'impronta protegge e' l'identita' (il vault), che e' un'altra cosa e sta in un altro posto.
 */
@Database(
  entities = [
    ChatEntity::class,
    MessageEntity::class,
    ContactEntity::class,
    StoryEntity::class,
    KeyringEntity::class,
    OutboxEntity::class,
    GroupMemberEntity::class,
  ],
  version = 8,
  exportSchema = true,
)
abstract class CodexDatabase : RoomDatabase() {

  abstract fun chats(): ChatDao

  abstract fun messages(): MessageDao

  abstract fun contacts(): ContactDao

  abstract fun stories(): StoryDao

  abstract fun keyring(): KeyringDao

  abstract fun outbox(): OutboxDao

  abstract fun groupMembers(): GroupMemberDao

  companion object {
    private const val NAME = "codex.db"

    /**
     * La libreria nativa di SQLCipher va caricata a mano.
     *
     * Non lo fa l'AAR: senza questa riga la prima query muore con `UnsatisfiedLinkError`, e il
     * messaggio non dice affatto che manca una `loadLibrary`. Si carica una volta per processo.
     */
    private fun loadNativeLibrary() {
      synchronized(this) {
        if (nativeLibraryLoaded) return
        System.loadLibrary("sqlcipher")
        nativeLibraryLoaded = true
      }
    }

    @Volatile
    private var nativeLibraryLoaded = false

    /**
     * Le storie, aggiunte dopo.
     *
     * Scritta a mano invece di lasciar ricreare il database: i messaggi di qualcuno non si buttano
     * via per aggiungere una tabella, nemmeno prima del rilascio. La regola vale da subito, o non
     * vale.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `stories` (
            `id` TEXT NOT NULL,
            `authorId` TEXT NOT NULL,
            `mine` INTEGER NOT NULL,
            `envelope` BLOB NOT NULL,
            `technique` TEXT NOT NULL,
            `seed` INTEGER NOT NULL,
            `paintingId` TEXT,
            `createdAt` INTEGER NOT NULL,
            `expiresAt` INTEGER NOT NULL,
            `seenAt` INTEGER,
            PRIMARY KEY(`id`)
          )
          """.trimIndent(),
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_expiresAt` ON `stories` (`expiresAt`)")
      }
    }

    /**
     * Il portachiavi, aggiunto con il pairing.
     *
     * Chi ha gia' l'app installata ha una chat con se stesso e dei messaggi dentro: la tabella si
     * aggiunge vuota e non tocca niente di quello che c'e'. La chiave delle note a se' non passa
     * di qui -- nasce ogni volta dal seme del portachiavi -- quindi dopo questa migrazione tutto
     * quello che si leggeva prima si legge ancora.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `chat_keys` (
            `chatId` TEXT NOT NULL,
            `wrapped` BLOB NOT NULL,
            `epoch` INTEGER NOT NULL,
            `storedAt` INTEGER NOT NULL,
            PRIMARY KEY(`chatId`)
          )
          """.trimIndent(),
        )
      }
    }

    /**
     * La posta in uscita, arrivata con la consegna nel cloud.
     *
     * Si aggiunge vuota: chi aveva gia' l'app ha conversazioni locali, e per quelle non c'e' niente
     * da consegnare a nessuno.
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `outbox` (
            `messageId` TEXT NOT NULL,
            `chatId` TEXT NOT NULL,
            `attempts` INTEGER NOT NULL,
            `lastAttemptAt` INTEGER NOT NULL,
            `queuedAt` INTEGER NOT NULL,
            PRIMARY KEY(`messageId`)
          )
          """.trimIndent(),
        )
      }
    }

    /**
     * Le ricevute, arrivate con le spunte vere.
     *
     * Due colonne con valore di partenza zero: le conversazioni che c'erano prima risultano "mai
     * ricevute ne' lette", che e' vero -- prima non lo registrava nessuno.
     */
    private val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `chats` ADD COLUMN `myDeliveredAt` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `chats` ADD COLUMN `myReadAt` INTEGER NOT NULL DEFAULT 0")
      }
    }

    /**
     * I gruppi: i membri, e un portachiavi che tiene **tutte** le epoche.
     *
     * La chiave primaria di `chat_keys` diventa la coppia (conversazione, epoca). SQLite non sa
     * cambiare una chiave primaria, quindi la tabella si rifa' e si ricopia -- e le righe che
     * c'erano, tutte a epoca zero, passano identiche: nessuna conversazione diventa illeggibile.
     */
    private val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `chat_keys_nuova` (
            `chatId` TEXT NOT NULL,
            `wrapped` BLOB NOT NULL,
            `epoch` INTEGER NOT NULL,
            `storedAt` INTEGER NOT NULL,
            PRIMARY KEY(`chatId`, `epoch`)
          )
          """.trimIndent(),
        )
        connection.execSQL(
          "INSERT OR REPLACE INTO `chat_keys_nuova` SELECT `chatId`, `wrapped`, `epoch`, `storedAt` FROM `chat_keys`",
        )
        connection.execSQL("DROP TABLE `chat_keys`")
        connection.execSQL("ALTER TABLE `chat_keys_nuova` RENAME TO `chat_keys`")
        connection.execSQL(
          """
          CREATE TABLE IF NOT EXISTS `group_members` (
            `chatId` TEXT NOT NULL,
            `codexId` TEXT NOT NULL,
            `uid` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `admin` INTEGER NOT NULL,
            `joinedAt` INTEGER NOT NULL,
            `gone` INTEGER NOT NULL,
            PRIMARY KEY(`chatId`, `codexId`)
          )
          """.trimIndent(),
        )
      }
    }

    /**
     * Le storie che arrivano da fuori.
     *
     * Due colonne: la chiave avvolta -- una storia di qualcun altro nasce con una chiave che questo
     * telefono non sa ricalcolare -- e l'account di chi l'ha scritta, che serve per dirgli "l'ho
     * vista". Le storie che c'erano prima sono tutte proprie: `null` e stringa vuota le descrivono
     * esattamente.
     */
    private val MIGRATION_6_7 = object : Migration(6, 7) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `stories` ADD COLUMN `wrappedKey` BLOB")
        connection.execSQL("ALTER TABLE `stories` ADD COLUMN `authorUid` TEXT NOT NULL DEFAULT ''")
      }
    }

    /** Il blocco per conversazione: una colonna, spenta per tutte quelle che c'erano. */
    private val MIGRATION_7_8 = object : Migration(7, 8) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `chats` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0")
      }
    }

    fun open(context: Context, passphrase: ByteArray): CodexDatabase {
      loadNativeLibrary()
      // SQLCipher azzera la frase che riceve dopo averla usata: e' voluto, ed e' il motivo per cui
      // qui gliene arriva una copia e non l'originale del portachiavi.
      val factory = SupportOpenHelperFactory(passphrase.copyOf())
      return Room.databaseBuilder(context, CodexDatabase::class.java, NAME)
        .openHelperFactory(factory)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
        .build()
    }

    /** Cancella il file: fa parte del "cancella tutto". */
    fun delete(context: Context) {
      context.deleteDatabase(NAME)
    }
  }
}
