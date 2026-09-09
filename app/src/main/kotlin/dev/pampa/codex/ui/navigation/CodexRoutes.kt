package dev.pampa.codex.ui.navigation

/** Le rotte della barra e quelle secondarie. Stringhe stabili: sono chiavi, non testo. */
object CodexRoutes {
  const val Chats = "chats"
  const val Stories = "stories"
  const val Me = "me"

  /** Una conversazione. Non ha la barra in basso: e' una pagina spinta, non una destinazione. */
  const val Chat = "chat/{chatId}"

  fun chat(chatId: String): String = "chat/$chatId"

  /** Le impostazioni di una conversazione. */
  const val ChatInfo = "chat/{chatId}/info"

  fun chatInfo(chatId: String): String = "chat/$chatId/info"

  /** Le persone: la lista, il proprio sigillo, chi aggiungere. */
  const val Contacts = "contacts"

  /** La propria scheda pubblica, da far inquadrare. */
  const val MySeal = "contacts/mine"

  /** Inquadrare o incollare la scheda di qualcun altro. */
  const val AddContact = "contacts/add"

  /**
   * Il rito della parola d'ordine con una persona.
   *
   * Il Codex ID sta nella rotta e non in uno stato condiviso perche' ci si arriva da tre punti
   * diversi -- dopo una scansione, da un contatto ancora chiuso, o rifacendo il rito -- e in tutti
   * e tre la schermata deve poter essere ricostruita da sola dopo che il sistema ha ucciso il
   * processo.
   */
  const val Ritual = "contacts/{codexId}/ritual"

  fun ritual(codexId: String): String = "contacts/$codexId/ritual"

  /** Un gruppo nuovo: nome e persone. */
  const val NewGroup = "groups/new"

  /** Entrare in un gruppo con l'invito di qualcuno. */
  const val JoinGroup = "groups/join"

  /** Le origini: il cifrario del prof e la storia dell'app. */
  const val Origins = "me/origins"

  /** Chi ha dipinto i quadri, chi ha disegnato le rune, cosa c'e' dentro l'APK. */
  const val Credits = "me/credits"

  /** Il banco di prova dei sigilli: esiste solo nelle build di lavoro. */
  const val Playground = "me/playground"
}
