package dev.pampa.codex.ui.chat

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.pampa.codex.data.chat.ChatMessage
import dev.pampa.codex.data.chat.ChatRepository
import dev.pampa.codex.data.chat.DEFAULT_PAGE
import dev.pampa.codex.data.contacts.ContactRepository
import dev.pampa.codex.crypto.MessageBody
import dev.pampa.codex.data.prefs.CodexPreferences
import dev.pampa.codex.data.db.ChatEntity
import dev.pampa.codex.data.db.ChatKind
import dev.pampa.codex.model.SealChoice
import dev.pampa.codex.model.Technique
import dev.pampa.codex.seal.PaintingShare
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Quali messaggi sono aperti **adesso**.
 *
 * Vive nel ViewModel e muore con la schermata, ed e' esattamente il comportamento voluto: uscire
 * dalla chat richiude tutti i sigilli. Non e' una preferenza salvata perche' non deve sopravvivere
 * a niente -- e' il motivo per cui una chat lasciata aperta sul tavolo non e' una chat leggibile.
 */
class RevealSession {
  private val open = MutableStateFlow<Map<String, Boolean>>(emptyMap())

  /** La mappa da id a "senza rituale": `true` significa aperto in fretta da "Rivela tutto". */
  val state: StateFlow<Map<String, Boolean>> = open.asStateFlow()

  fun reveal(id: String, instant: Boolean = false) {
    open.update { it + (id to instant) }
  }

  fun revealAll(ids: Collection<String>) {
    open.update { current -> current + ids.associateWith { true } }
  }

  fun sealAgain() {
    open.value = emptyMap()
  }
}

/** Cosa fare con un messaggio tenuto premuto. Per ora: solo il quadro, solo se si puo' esportare. */
data class MessageActions(val message: ChatMessage? = null)

data class ComposerState(
  val draft: String = "",
  val choice: SealChoice = SealChoice(),
  val sending: Boolean = false,
  val chooserOpen: Boolean = false,
  /** "Visualizza una volta": si apre, si legge, e non c'e' piu'. */
  val viewOnce: Boolean = false,
)

/**
 * Una registrazione in corso.
 *
 * La forma d'onda cresce mentre si parla: e' quello che distingue "sta registrando" da "si e'
 * bloccato", e costa un campione ogni decimo di secondo.
 */
data class RecordingState(
  val active: Boolean = false,
  val millis: Long = 0,
  val waveform: ByteArray = ByteArray(0),
) {
  override fun equals(other: Any?): Boolean =
    other is RecordingState && active == other.active && millis == other.millis &&
      waveform.contentEquals(other.waveform)

  override fun hashCode(): Int =
    31 * (31 * active.hashCode() + millis.hashCode()) + waveform.contentHashCode()
}

/** Cosa si legge sotto il nome, in cima a una conversazione. */
enum class ChatPresence { None, Online, Typing }

/** Oltre questo tempo dall'ultimo battito, "online" non vuol piu' dire niente. */
private const val ONLINE_WINDOW = 90_000L

/**
 * Il limite di un allegato: trenta megabyte, gli stessi che accettano le regole del deposito.
 *
 * Il controllo si fa **prima** di cifrare: cifrare mezzo gigabyte per poi sentirsi dire di no dal
 * server sarebbe un minuto di attesa buttato, con il telefono che scalda.
 */
private const val MAX_MEDIA_BYTES = 30L * 1024 * 1024

/**
 * Una conversazione.
 *
 * Il repository consegna i messaggi gia' aperti (l'app e' sbloccata, altrimenti non saremmo qui);
 * questo strato decide soltanto **quali sono visibili in questo momento**, che e' una cosa diversa
 * e molto piu' effimera.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
  @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
  private val chatRepository: ChatRepository,
  private val contacts: ContactRepository,
  private val preferences: CodexPreferences,
  private val paintingShare: PaintingShare,
  private val groups: dev.pampa.codex.data.groups.GroupRepository,
  nearbyEngine: dev.pampa.codex.data.nearby.NearbyEngine,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  val chatId: String = checkNotNull(savedStateHandle["chatId"])

  val chat: StateFlow<ChatEntity?> = chatRepository.observeChat(chatId)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /**
   * Quanti messaggi si stanno guardando.
   *
   * Cresce quando si arriva in cima e non torna mai indietro: chi risale una conversazione lunga non
   * vuole vedersela riaccorciare sotto le dita mentre scorre.
   */
  private val window = MutableStateFlow(DEFAULT_PAGE)

  /**
   * `null` finche' non si sa: e' diverso da "vuoto".
   *
   * Con `emptyList()` come valore di partenza, ogni apertura mostrava per un istante il cartello
   * "non c'e' niente" prima che arrivasse la prima emissione. Su un telefono e' un lampo; sui due
   * pannelli del tablet, dove la lista resta in scena, si legge benissimo. Il cartello adesso
   * compare solo quando la risposta e' arrivata **ed e' vuota**.
   */
  val messages: StateFlow<List<ChatMessage>?> = window
    .flatMapLatest { limit -> chatRepository.observeMessages(chatId, limit) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  /** Ce ne sono altri piu' indietro? Se ne sono arrivati esattamente quanti se ne chiedevano, si'. */
  fun hasOlder(): Boolean = messages.value.orEmpty().size >= window.value

  fun loadOlder() {
    if (hasOlder()) window.update { it + DEFAULT_PAGE }
  }

  val session = RevealSession()

  private val _composer = MutableStateFlow(ComposerState())
  val composer: StateFlow<ComposerState> = _composer.asStateFlow()

  private val _actions = MutableStateFlow(MessageActions())
  val actions: StateFlow<MessageActions> = _actions.asStateFlow()

  /** L'intento di condivisione appena preparato: la schermata lo consuma e lo lancia. */
  private val _shareRequest = MutableStateFlow<Intent?>(null)
  val shareRequest: StateFlow<Intent?> = _shareRequest.asStateFlow()

  init {
    viewModelScope.launch {
      chatRepository.clearUnread(chatId)
      // I messaggi a scadenza se ne vanno quando si apre la conversazione: e' l'unico momento in
      // cui serve che siano spariti, e non costa un processo in background per ogni chat.
      runCatching { chatRepository.sweepExpired() }
    }
  }

  /**
   * Quello che si sta scrivendo, e il fatto che lo si stia facendo.
   *
   * Il "sta scrivendo" parte da qui e non da un timer: e' il tasto premuto a dirlo. Chi lo riceve
   * lo limita da se' a una volta ogni tre secondi -- una frase di quaranta caratteri non sono
   * quaranta scritture.
   */
  fun setDraft(text: String) {
    _composer.update { it.copy(draft = text) }
    if (text.isNotBlank()) viewModelScope.launch { chatRepository.reportTyping(chatId) }
  }

  /**
   * L'ultima volta che l'altra persona e' stata vista, se ha un account e se lo lascia sapere.
   *
   * Il suo `uid` non sta nella conversazione -- li' c'e' il Codex ID, che e' l'identita' -- quindi
   * si passa dai contatti, che sanno tradurre l'uno nell'altro (cercandolo, se serve).
   */
  private val peerLastSeen: Flow<Long> = chat
    .map { it?.peerId }
    .distinctUntilChanged()
    .flatMapLatest { peerId ->
      if (peerId == null) {
        flowOf(0L)
      } else {
        flow { emit(contacts.peerUid(peerId)) }.flatMapLatest { uid ->
          if (uid == null) flowOf(0L) else chatRepository.observePeerPresence(uid)
        }
      }
    }

  /**
   * Cosa c'e' scritto sotto il nome, in cima alla conversazione.
   *
   * Tre cose che si escludono, in ordine di quanto sono vive: sta scrivendo, e' online, oppure
   * l'istruzione di sempre. Chi ha spento questi due segnali non li manda **e non li vede**.
   */
  val presence: StateFlow<ChatPresence> = combine(
    preferences.socialSignals,
    chatRepository.observePeerTyping(chatId),
    peerLastSeen,
  ) { signals, typing, lastSeen ->
    when {
      signals.showTyping && typing -> ChatPresence.Typing
      signals.showOnline && lastSeen > 0 &&
        System.currentTimeMillis() - lastSeen < ONLINE_WINDOW -> ChatPresence.Online
      else -> ChatPresence.None
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatPresence.None)

  /**
   * Se l'altra persona e' **qui**, nella stessa stanza.
   *
   * Non e' un dettaglio tecnico da nascondere: quando e' vero, i messaggi non passano da nessun
   * server, e chi scrive ha diritto di saperlo -- e' la differenza fra "l'ha visto un'azienda" e
   * "non l'ha visto nessuno".
   */
  val nearby: StateFlow<Boolean> = combine(chat, nearbyEngine.nearbyPeople) { chat, vicini ->
    val peer = chat?.peerId
    peer != null && peer in vicini
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  // --- I gruppi ------------------------------------------------------------------------------

  /**
   * Com'e' messo un gruppo: quante persone, e se la chiave e' arrivata.
   *
   * `null` per una chat a due, che di gruppi non ha niente. La chiave si guarda **qui** e non nei
   * messaggi: un gruppo appena raggiunto da un invito e' vuoto, quindi dai messaggi non si capirebbe
   * niente, e "in attesa della chiave" e' proprio la cosa da dire in quel momento.
   */
  data class GroupState(val people: Int, val hasKey: Boolean)

  val group: StateFlow<GroupState?> = combine(chat, groups.observeMembers(chatId)) { chat, members ->
    if (chat?.kind != ChatKind.GROUP) {
      null
    } else {
      GroupState(
        people = members.count { !it.gone },
        hasKey = chatRepository.hasKey(chatId),
      )
    }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  // --- Le foto -------------------------------------------------------------------------------

  /**
   * Il contenuto in chiaro degli allegati aperti, **solo in memoria**.
   *
   * Vive nel ViewModel e muore con la schermata, come i sigilli aperti: uscire dalla conversazione
   * non lascia in giro nessuna foto decifrata. E' anche cio' che rende vero il "visualizza una
   * volta" -- non c'e' nessuna copia da dimenticare di cancellare.
   */
  private val _mediaBytes = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
  val mediaBytes: StateFlow<Map<String, ByteArray>> = _mediaBytes.asStateFlow()

  private val loading = mutableSetOf<String>()

  /**
   * Porta in memoria l'allegato di un messaggio, scaricandolo se serve.
   *
   * Si chiama quando il sigillo si apre, non quando il messaggio arriva: una conversazione con
   * duecento foto non deve tirarsele giu' tutte per essere stata aperta una volta.
   */
  fun loadMedia(messageId: String) {
    if (_mediaBytes.value.containsKey(messageId) || !loading.add(messageId)) return
    viewModelScope.launch {
      runCatching {
        if (!chatRepository.ensureMedia(messageId)) return@runCatching
        chatRepository.openMedia(messageId)?.let { bytes ->
          _mediaBytes.update { it + (messageId to bytes) }
        }
      }
      loading.remove(messageId)
    }
  }

  /**
   * Manda una foto.
   *
   * Le misure si leggono **prima** di cifrare, dall'intestazione dell'immagine e senza caricarla:
   * servono a disegnare il posto giusto dall'altra parte prima che il file sia arrivato.
   */
  fun sendPhoto(uri: android.net.Uri) {
    viewModelScope.launch {
      val resolver = context.contentResolver
      val (width, height) = runCatching {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
        options.outWidth to options.outHeight
      }.getOrDefault(0 to 0)
      val size = runCatching {
        resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
      }.getOrNull() ?: return@launch
      if (size <= 0) return@launch
      // Il limite non e' un capriccio: e' lo stesso che le regole dello Storage fanno rispettare, e
      // scoprirlo con un caricamento rifiutato dopo trenta secondi sarebbe la cosa peggiore.
      if (size > MAX_MEDIA_BYTES) {
        _notice.value = dev.pampa.codex.R.string.media_too_big
        return@launch
      }
      val mime = resolver.getType(uri) ?: "image/jpeg"
      val stream = runCatching { resolver.openInputStream(uri) }.getOrNull() ?: return@launch

      chatRepository.sendMedia(
        chatId = chatId,
        kind = MessageBody.Media.Kind.PHOTO,
        mime = mime,
        source = stream,
        plainLength = size,
        width = width,
        height = height,
        choice = _composer.value.choice,
        viewOnce = _composer.value.viewOnce,
      )
      _composer.update { it.copy(viewOnce = false) }
    }
  }

  // --- Le note vocali ------------------------------------------------------------------------

  private val recorder = VoiceRecorder(context)

  private val _recording = MutableStateFlow(RecordingState())
  val recording: StateFlow<RecordingState> = _recording.asStateFlow()

  /**
   * Comincia a registrare.
   *
   * Il permesso lo chiede la schermata: qui si sa solo se c'e'. Se il registratore non parte --
   * capita quando un'altra app tiene il microfono -- lo stato torna com'era, senza barre finte.
   */
  fun startRecording() {
    if (_recording.value.active) return
    recorder.discardLeftovers()
    if (!recorder.start()) return
    _recording.value = RecordingState(active = true)
    viewModelScope.launch {
      while (_recording.value.active) {
        kotlinx.coroutines.delay(100)
        recorder.sample()
        _recording.update {
          if (!it.active) it else it.copy(millis = it.millis + 100, waveform = recorder.currentWaveform())
        }
      }
    }
  }

  /** Chiude la registrazione e la manda. Una nota troppo corta non parte, e si dice perche'. */
  fun stopRecordingAndSend() {
    if (!_recording.value.active) return
    _recording.value = RecordingState()
    val registrazione = recorder.stop()
    if (registrazione == null) {
      _notice.value = dev.pampa.codex.R.string.voice_too_short
      return
    }
    viewModelScope.launch {
      val file = registrazione.file
      try {
        chatRepository.sendMedia(
          chatId = chatId,
          kind = MessageBody.Media.Kind.VOICE,
          mime = "audio/mp4",
          source = file.inputStream(),
          plainLength = file.length(),
          durationMs = registrazione.durationMs,
          waveform = registrazione.waveform,
          choice = _composer.value.choice,
          viewOnce = _composer.value.viewOnce,
        )
      } finally {
        // Il file in chiaro esiste solo fra il registratore e la cifratura: qui finisce il suo
        // tempo, e ci finisce anche se l'invio e' andato storto.
        file.delete()
      }
      _composer.update { it.copy(viewOnce = false) }
    }
  }

  fun cancelRecording() {
    if (!_recording.value.active) return
    _recording.value = RecordingState()
    recorder.cancel()
  }

  override fun onCleared() {
    // Uscire dalla chat mentre si registra non deve lasciare acceso il microfono ne' l'audio nella
    // cache: e' la stessa regola dei sigilli, che uscendo si richiudono.
    recorder.cancel()
    recorder.discardLeftovers()
    super.onCleared()
  }

  // --- Quello che va detto a chi guarda --------------------------------------------------------

  private val _notice = MutableStateFlow<Int?>(null)

  /** Una frase da mostrare una volta sola: un limite superato, una registrazione troppo corta. */
  val notice: StateFlow<Int?> = _notice.asStateFlow()

  fun clearNotice() {
    _notice.value = null
  }

  /**
   * Tenere premuto un messaggio: apre quello che ci si puo' fare.
   *
   * Anche su un sigillo ancora chiuso, perche' "elimina" deve funzionare comunque: un messaggio
   * che si puo' togliere solo dopo averlo aperto e' un messaggio che non si puo' togliere.
   */
  fun openActions(message: ChatMessage) {
    _actions.value = MessageActions(message)
  }

  fun closeActions() {
    _actions.value = MessageActions()
  }

  /**
   * Il quadro diventa un PNG con il messaggio dentro.
   *
   * Il file lo scrive il livello di sotto; qui si prepara solo l'intento, perche' lanciarlo tocca
   * alla schermata: un ViewModel che apre un'altra app e' un ViewModel che non si puo' provare.
   */
  fun sharePainting(subject: String) {
    val message = _actions.value.message ?: return
    closeActions()
    viewModelScope.launch {
      val export = chatRepository.exportable(message.id).getOrNull() ?: return@launch
      val uri = paintingShare.writePng(export).getOrNull() ?: return@launch
      _shareRequest.value = paintingShare.shareIntent(uri, subject)
    }
  }

  fun shareLaunched() {
    _shareRequest.value = null
  }

  /** Cancella il messaggio del menu. Solo da questo telefono: non esiste ancora "elimina per tutti". */
  fun deleteSelected() {
    val message = _actions.value.message ?: return
    closeActions()
    viewModelScope.launch { chatRepository.deleteMessage(message.id) }
  }

  fun openChooser() = _composer.update { it.copy(chooserOpen = true) }

  fun closeChooser() = _composer.update { it.copy(chooserOpen = false) }

  /** `null` significa "a sorpresa": la tecnica la decide il seme del messaggio. */
  fun setTechnique(technique: Technique?) {
    _composer.update { it.copy(choice = SealChoice(technique = technique), chooserOpen = false) }
  }

  fun setViewOnce(enabled: Boolean) = _composer.update { it.copy(viewOnce = enabled) }

  fun send() {
    val state = _composer.value
    if (state.draft.isBlank() || state.sending) return
    _composer.update { it.copy(sending = true) }
    viewModelScope.launch {
      val result = chatRepository.send(chatId, state.draft, state.choice, state.viewOnce)
      _composer.update {
        // "Visualizza una volta" si spegne dopo l'invio: e' una scelta per **questo** messaggio, e
        // lasciarla accesa farebbe sparire il prossimo senza che nessuno l'abbia chiesto.
        if (result.isSuccess) {
          it.copy(draft = "", sending = false, viewOnce = false)
        } else {
          it.copy(sending = false)
        }
      }
    }
  }

  fun reveal(message: ChatMessage) {
    session.reveal(message.id)
    if (!message.revealedOnce) {
      viewModelScope.launch { chatRepository.markRevealed(message.id) }
    }
  }

  /**
   * Riapre in un colpo solo i sigilli **gia' visti almeno una volta**.
   *
   * I mai aperti restano chiusi apposta: il rituale di un messaggio nuovo si fa una volta e non si
   * salta, altrimenti tanto varrebbe mostrare il testo.
   */
  fun revealAll() {
    session.revealAll(messages.value.orEmpty().filter { it.revealedOnce && !it.burned }.map { it.id })
  }

  fun sealAgain() = session.sealAgain()

  /**
   * C'e' un "visualizza una volta" aperto adesso: la finestra non deve finire nelle miniature.
   *
   * **E' un flusso e non una funzione**, e la differenza qui vale la protezione intera. Come
   * funzione veniva chiamata durante la composizione della schermata, ma la schermata non leggeva
   * niente che cambiasse quando un sigillo si apriva: nessuna ricomposizione, nessun ricalcolo, e
   * `FLAG_SECURE` restava spento per sempre. Sembrava acceso leggendo il codice, e non lo era mai
   * stato -- si e' visto solo aprendo un "visualizza una volta" e riuscendo a fotografarlo.
   */
  val secureNeeded: StateFlow<Boolean> = combine(session.state, messages) { open, list ->
    list.orEmpty().any { it.id in open.keys && it.viewOnce && !it.burned }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

  /**
   * Si esce dalla conversazione: i "visualizza una volta" aperti si consumano.
   *
   * Qui e non alla fine dell'animazione. Un testo che si cancella mentre lo si sta ancora leggendo
   * non e' "visualizza una volta": e' "visualizza per un secondo".
   */
  fun leaving() {
    val open = session.state.value.keys.toList()
    session.sealAgain()
    if (open.isEmpty()) return
    // Fuori dallo scope del ViewModel: sta per morire, e il lavoro deve finire comunque.
    ProcessScope.launch { runCatching { chatRepository.burnViewed(open) } }
  }

  /** Quanti messaggi "Rivela tutto" riaprirebbe: sotto uno, il tasto non ha senso e non compare. */
  fun revealableCount(): Int = messages.value.orEmpty().count { it.revealedOnce && !it.burned }
}

/**
 * Lo scope che sopravvive alla schermata.
 *
 * Serve a una cosa sola: bruciare i "visualizza una volta" mentre si esce. Il `viewModelScope`
 * viene cancellato proprio in quel momento, e un messaggio che doveva sparire resterebbe li'.
 */
private val ProcessScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
