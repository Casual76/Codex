package dev.pampa.codex.ui.chat

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTone
import androidx.compose.runtime.CompositionLocalProvider
import dev.pampa.codex.ui.theme.CodexSheetColumn
import dev.antigravity.fluidengine.ui.tutorial.FluidGestureHint
import dev.antigravity.fluidengine.ui.tutorial.fluidTutorialAnchor
import dev.pampa.codex.ui.tutorial.CodexHint
import dev.pampa.codex.ui.tutorial.CodexHintOffer
import dev.pampa.codex.R
import dev.pampa.codex.crypto.MessageBody
import dev.pampa.codex.data.chat.ChatMessage
import dev.pampa.codex.data.db.MessageStatus
import dev.pampa.codex.model.Technique
import dev.pampa.codex.ui.lock.LocalBiometricAuthenticator
import dev.pampa.codex.ui.lock.confirmIdentity
import dev.pampa.codex.ui.seal.SealBubble
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Una conversazione.
 *
 * Tre cose la distinguono da qualsiasi altra chat:
 *
 * - i messaggi arrivano **chiusi** e si aprono con un tocco;
 * - uscendo si richiudono tutti, e rientrando c'e' di nuovo da aprirli;
 * - "Rivela tutto" riapre in un colpo solo quelli gia' visti, e soltanto quelli.
 */
@Composable
fun ChatScreen(
  onBack: () -> Unit,
  onOpenInfo: () -> Unit = {},
  viewModel: ChatViewModel = hiltViewModel(),
) {
  val chat by viewModel.chat.collectAsStateWithLifecycle()
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val revealed by viewModel.session.state.collectAsStateWithLifecycle()
  val composer by viewModel.composer.collectAsStateWithLifecycle()
  val actions by viewModel.actions.collectAsStateWithLifecycle()
  val shareRequest by viewModel.shareRequest.collectAsStateWithLifecycle()
  val presence by viewModel.presence.collectAsStateWithLifecycle()
  val gruppo by viewModel.group.collectAsStateWithLifecycle()
  val vicino by viewModel.nearby.collectAsStateWithLifecycle()
  val mediaBytes by viewModel.mediaBytes.collectAsStateWithLifecycle()
  val recording by viewModel.recording.collectAsStateWithLifecycle()
  val notice by viewModel.notice.collectAsStateWithLifecycle()

  /** La foto aperta a tutto schermo, se ce n'e' una. */
  var viewing by remember { mutableStateOf<String?>(null) }

  // Il microfono si chiede quando si prova a registrare, non all'avvio: un permesso chiesto prima
  // di aver mostrato a cosa serve e' un permesso negato.
  var microphoneRefused by remember { mutableStateOf(false) }
  val askMicrophone = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) viewModel.startRecording() else microphoneRefused = true
  }

  // Il selettore di foto del sistema: mostra solo le immagini, e **non chiede nessun permesso** --
  // e' lui a leggere il file e a passarci un solo URI. Chiedere l'accesso a tutta la galleria per
  // mandare una foto sarebbe chiedere mille volte quello che serve.
  val pickPhoto = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri -> uri?.let(viewModel::sendPhoto) }
  val listState = rememberLazyListState()
  val haptics = rememberFluidHaptics()
  val context = LocalContext.current
  val shareTitle = stringResource(R.string.painting_share)
  val shareSubject = stringResource(R.string.painting_share_subject)

  // Finche' c'e' un "visualizza una volta" aperto la finestra e' protetta: niente schermate, e
  // niente anteprima nell'elenco delle app aperte. Si toglie appena si richiude.
  val secure by viewModel.secureNeeded.collectAsStateWithLifecycle()
  DisposableEffect(secure) {
    // `LocalContext` non e' l'Activity: e' un contesto **avvolto** (Hilt ne mette uno suo), e il
    // cast diretto restituiva null in silenzio. La finestra non veniva mai protetta, e non c'era
    // niente che lo dicesse -- si e' visto solo provando a fare uno screenshot con un "visualizza
    // una volta" aperto e trovandoci dentro la foto.
    val window = context.findActivity()?.window
    if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    onDispose { if (secure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
  }

  // Uscendo, i "visualizza una volta" gia' aperti si consumano.
  DisposableEffect(Unit) {
    onDispose { viewModel.leaving() }
  }

  // Il selettore di app lo apre la schermata: e' l'unico posto che ha un Context da cui partire.
  LaunchedEffect(shareRequest) {
    val intent = shareRequest ?: return@LaunchedEffect
    context.startActivity(Intent.createChooser(intent, shareTitle))
    viewModel.shareLaunched()
  }

  val timeline = remember(messages) { timelineOf(messages.orEmpty()) }

  // Il sigillo che il suggerimento indica: l'ultimo ricevuto ancora chiuso. L'ultimo e non il
  // primo perche' la conversazione sta gia' in fondo, e un alone su un messaggio fuori schermo
  // sarebbe una freccia verso il nulla.
  val sealToOpen = remember(messages, revealed) {
    messages?.lastOrNull { !it.outgoing && !it.isPlaceholder && !revealed.containsKey(it.id) }?.id
  }
  CodexHintOffer(
    id = CodexHint.SealTap,
    priority = 30,
    title = stringResource(R.string.hint_seal_title),
    text = stringResource(R.string.hint_seal_text),
    gesture = FluidGestureHint.Tap,
    ready = sealToOpen != null,
  )
  // La scelta della tecnica si spiega quando c'e' qualcosa da mandare: a casella vuota il tasto
  // indicato non e' nemmeno quello.
  CodexHintOffer(
    id = CodexHint.SendHold,
    priority = 20,
    title = stringResource(R.string.hint_send_title),
    text = stringResource(R.string.hint_send_text),
    gesture = FluidGestureHint.LongPress,
    ready = composer.draft.isNotBlank(),
  )

  // Arrivati in cima, si chiedono i precedenti. La soglia e' qualche riga prima del bordo: chiederli
  // quando il dito e' gia' fermo sul primo messaggio vorrebbe dire far vedere il salto.
  LaunchedEffect(listState) {
    snapshotFlow { listState.firstVisibleItemIndex }
      .collect { first -> if (first <= HEADER_ITEMS + 3) viewModel.loadOlder() }
  }

  // Il messaggio appena arrivato deve essere quello che si vede: senza, si scrive e non succede
  // niente sullo schermo.
  LaunchedEffect(timeline.size) {
    if (timeline.isNotEmpty()) listState.animateScrollToItem(timeline.lastIndex + HEADER_ITEMS)
  }

  // **La serratura della conversazione.**
  //
  // Vive nella schermata e non altrove: uscendo e rientrando si richiede, ed e' voluto. Una
  // serratura che si apre una volta al giorno protegge il primo dei trenta secondi in cui il
  // telefono e' in mano a un altro, e non il secondo.
  var unlocked by remember(chat?.id) { mutableStateOf(false) }
  // Il prompt di sistema si aggancia al gestore dei fragment: senza una `FragmentActivity` non
  // esiste, e la conversazione resta chiusa invece di aprirsi per un controllo che non c'e' stato.
  val activity = context.findActivity() as? androidx.fragment.app.FragmentActivity
  val authenticator = LocalBiometricAuthenticator.current
  val promptTitle = stringResource(R.string.chat_locked_prompt)
  val promptDetail = stringResource(R.string.chat_locked_detail)

  fun askIdentity() {
    val act = activity
    if (authenticator == null || act == null) {
      // Senza un'Activity non c'e' prompt di sistema, e senza prompt non si finge un controllo: la
      // conversazione resta chiusa.
      return
    }
    authenticator.confirmIdentity(
      activity = act,
      title = promptTitle,
      subtitle = promptDetail,
      onSuccess = { unlocked = true },
      onCancel = { },
    )
  }

  // Entrando in una conversazione bloccata il prompt arriva da solo: chiedere un tocco in piu' per
  // arrivare a un tocco che apre il sistema sarebbe un passaggio inventato.
  LaunchedEffect(chat?.id, chat?.locked) {
    if (chat?.locked == true && !unlocked) askIdentity()
  }

  val gruppoOra = gruppo
  FluidScreen(
    title = chat?.title.orEmpty(),
    // Sotto il nome: cosa sta succedendo adesso, se sta succedendo qualcosa. Altrimenti
    // l'istruzione di sempre, che per chi apre Codex la prima volta e' la cosa piu' utile.
    subtitle = when {
      // Un gruppo dice quante persone ci sono dentro. Se la chiave non e' ancora arrivata lo dice
      // **al posto** di tutto il resto: e' l'unica cosa che spiega perche' non si apre niente.
      gruppoOra != null && !gruppoOra.hasKey -> stringResource(R.string.group_waiting_key)
      gruppoOra != null -> stringResource(R.string.group_people, gruppoOra.people)
      // "Vicino" viene prima di "online": e' piu' vero. Se e' nella stanza, quello che vi scrivete
      // non passa da nessuna parte.
      vicino -> stringResource(R.string.chat_nearby)
      presence == ChatPresence.Typing -> stringResource(R.string.chat_typing)
      presence == ChatPresence.Online -> stringResource(R.string.chat_online)
      else -> stringResource(R.string.chat_subtitle)
    },
    onBack = onBack,
    listState = listState,
    extraBottomPadding = ComposerReservedHeight,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Glow) },
    actions = {
      if (viewModel.revealableCount() > 0) {
        FluidBarAction(
          icon = Icons.Rounded.LockOpen,
          contentDescription = stringResource(R.string.chat_reveal_all),
          onClick = viewModel::revealAll,
        )
      }
      FluidBarAction(
        icon = Icons.Rounded.Tune,
        // Un'etichetta, non la frase del sottotitolo: chi usa TalkBack si sente leggere questo al
        // posto dell'icona, e "Come si comporta questa conversazione." non e' il nome di un tasto.
        contentDescription = stringResource(R.string.chat_info_action),
        onClick = onOpenInfo,
      )
    },
    overlay = { backdrop ->
      if (chat?.locked == true && !unlocked) return@FluidScreen
      CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Composer(
          state = composer,
          recording = recording,
          backdrop = backdrop,
          onDraftChange = viewModel::setDraft,
          onAttach = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
          onRecord = {
            val permesso = ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (permesso) viewModel.startRecording() else askMicrophone.launch(Manifest.permission.RECORD_AUDIO)
          },
          onStopRecording = viewModel::stopRecordingAndSend,
          onCancelRecording = viewModel::cancelRecording,
          onSend = viewModel::send,
          onOpenChooser = viewModel::openChooser,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    },
  ) {
    if (chat?.locked == true && !unlocked) {
      item(key = "locked") {
        FluidEmptyState(
          title = stringResource(R.string.chat_locked_title),
          detail = stringResource(R.string.chat_locked_detail),
        )
      }
      item(key = "locked-action") {
        FluidButton(
          text = stringResource(R.string.chat_locked_open),
          onClick = { askIdentity() },
          fillWidth = true,
        )
      }
      return@FluidScreen
    }

    if (messages?.isEmpty() == true) {
      item(key = "empty") {
        FluidEmptyState(
          title = stringResource(R.string.chat_empty_title),
          detail = stringResource(R.string.chat_empty_detail),
        )
      }
    }
    items(count = timeline.size, key = { timeline[it].key }) { index ->
      when (val row = timeline[index]) {
        is TimelineRow.Day -> DaySeparator(row.date)
        is TimelineRow.Message -> MessageRow(
          message = row.message,
          revealed = revealed.containsKey(row.message.id),
          instant = revealed[row.message.id] == true,
          bytes = mediaBytes[row.message.id],
          onOpenPhoto = { viewing = row.message.id },
          onReveal = {
            viewModel.reveal(row.message)
            // Il file si scarica quando il sigillo si apre, non quando il messaggio arriva.
            if (row.message.media != null) viewModel.loadMedia(row.message.id)
          },
          onLongPress = {
            haptics.play(FluidHapticEvent.Threshold)
            viewModel.openActions(row.message)
          },
          hinted = row.message.id == sealToOpen,
        )
      }
    }
  }

  // La foto a tutto schermo. I byte sono gli stessi che sono gia' in memoria: aprirla non scarica
  // niente e non scrive niente.
  val watched = viewing?.let { id -> mediaBytes[id]?.let { id to it } }
  if (watched != null) {
    PhotoViewer(
      bytes = watched.second,
      // Un "visualizza una volta" guardato a tutto schermo va protetto **qui**: questa e' una
      // finestra diversa da quella dell'Activity, e il flag non si eredita.
      secure = messages?.firstOrNull { it.id == watched.first }?.viewOnce == true,
      onDismiss = { viewing = null },
    )
  }

  // Una frase e basta, quando qualcosa non si poteva fare: la foto troppo grande, la nota vocale
  // durata due decimi. Non e' un errore da capire, e' un fatto da sapere.
  val noticeText = notice
  if (noticeText != null) {
    FluidAlert(
      onDismissRequest = viewModel::clearNotice,
      title = stringResource(noticeText),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_ok),
          onClick = viewModel::clearNotice,
        ),
      ),
    )
  }

  // Il permesso negato non e' un guasto: si dice a cosa serviva e cosa succede all'audio, e la
  // conversazione continua senza note vocali.
  if (microphoneRefused) {
    FluidAlert(
      onDismissRequest = { microphoneRefused = false },
      title = stringResource(R.string.voice_permission_title),
      message = stringResource(R.string.voice_permission_detail),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_ok),
          onClick = { microphoneRefused = false },
        ),
      ),
    )
  }

  TechniqueChooser(
    open = composer.chooserOpen,
    selected = composer.choice.technique,
    viewOnce = composer.viewOnce,
    onDismiss = viewModel::closeChooser,
    onSelect = viewModel::setTechnique,
    onViewOnce = viewModel::setViewOnce,
  )

  val clipboard = LocalClipboardManager.current
  var confirmDelete by remember { mutableStateOf(false) }

  MessageMenu(
    message = actions.message,
    onDismiss = viewModel::closeActions,
    onShare = { viewModel.sharePainting(shareSubject) },
    onCopy = { text ->
      viewModel.closeActions()
      clipboard.setText(AnnotatedString(text))
    },
    onDelete = { confirmDelete = true },
  )

  // Cancellare un messaggio e' definitivo e non costava niente: pressione lunga, un tocco, e non
  // c'era piu'. Una domanda in mezzo e' l'unica cosa che sta fra una svista e una perdita.
  if (confirmDelete) {
    FluidAlert(
      onDismissRequest = { confirmDelete = false },
      title = stringResource(R.string.message_delete),
      message = stringResource(R.string.message_delete_confirm),
      actions = listOf(
        FluidAlertAction(
          label = stringResource(R.string.action_cancel),
          onClick = { confirmDelete = false },
        ),
        FluidAlertAction(
          label = stringResource(R.string.message_delete),
          emphasis = FluidAlertAction.Emphasis.Destructive,
          onClick = {
            confirmDelete = false
            viewModel.deleteSelected()
          },
        ),
      ),
    )
  }
}

/** Il giorno a cui appartengono i messaggi che seguono. */
@Composable
private fun DaySeparator(date: LocalDate) {
  val today = remember { LocalDate.now() }
  val label = when (date) {
    today -> stringResource(R.string.chat_today)
    today.minusDays(1) -> stringResource(R.string.chat_yesterday)
    else -> remember(date) {
      date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.getDefault()))
    }
  }
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = 10.dp),
    )
  }
}

/**
 * Cosa si puo' fare con un messaggio tenuto premuto.
 *
 * "Copia" compare solo su un messaggio **gia' aperto**: copiare il testo di un sigillo ancora
 * chiuso vorrebbe dire aggirare il rituale con una pressione lunga, che e' esattamente la
 * scorciatoia che l'app non deve avere.
 */
@Composable
private fun MessageMenu(
  message: ChatMessage?,
  onDismiss: () -> Unit,
  onShare: () -> Unit,
  onCopy: (String) -> Unit,
  onDelete: () -> Unit,
) {
  FluidGlassModalPortal(
    visible = message != null,
    onDismissRequest = onDismiss,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = stringResource(R.string.message_actions),
  ) {
    val current = message ?: return@FluidGlassModalPortal
    CodexSheetColumn(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
    ) {
      FluidListGroup {
        if (current.exportable) {
          FluidListRow(
            title = stringResource(R.string.painting_share),
            subtitle = stringResource(R.string.painting_share_detail),
            onClick = onShare,
          )
          FluidListDivider()
        }
        if (current.revealedOnce && !current.burned && current.text.isNotEmpty()) {
          FluidListRow(
            title = stringResource(R.string.message_copy),
            subtitle = stringResource(R.string.message_copy_detail),
            onClick = { onCopy(current.text) },
          )
          FluidListDivider()
        }
        FluidListRow(
          title = stringResource(R.string.message_delete),
          subtitle = stringResource(R.string.message_delete_detail),
          tone = FluidTone.Danger,
          onClick = onDelete,
        )
      }
    }
  }
}

/** Un messaggio nella conversazione: a destra i miei, a sinistra quelli degli altri. */
@Composable
private fun MessageRow(
  message: ChatMessage,
  revealed: Boolean,
  instant: Boolean,
  bytes: ByteArray?,
  onReveal: () -> Unit,
  onOpenPhoto: () -> Unit,
  onLongPress: () -> Unit,
  /** Vero sul sigillo che il suggerimento del primo uso indica: e' lui a portarsi l'alone. */
  hinted: Boolean = false,
) {
  // Su uno schermo largo una bolla sola su una riga da duemila pixel non si legge come una
  // conversazione: la colonna dei messaggi si ferma a una larghezza da lettura e sta in mezzo.
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
  Row(
    // L'ordine conta: prima il tetto, poi il riempimento. Al contrario `fillMaxWidth` fissa gia'
    // la larghezza minima a quella dello schermo e il tetto non ha piu' niente da limitare.
    modifier = Modifier
      .widthIn(max = ChatContentWidth)
      .fillMaxWidth(),
    horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start,
  ) {
    if (message.isPlaceholder) {
      // Tre motivi diversi per non poter aprire un messaggio, e tre frasi diverse. "Sigillato per
      // sempre" al posto di "manca la parola d'ordine" manderebbe qualcuno a cercare un guasto che
      // non c'e', o -- peggio -- a rassegnarsi a un messaggio che basterebbe rifare il rito per
      // leggere.
      Text(
        text = stringResource(
          when (message.content) {
            ChatMessage.Content.SEALED -> R.string.message_sealed
            ChatMessage.Content.UNREADABLE -> R.string.message_unreadable
            ChatMessage.Content.OPEN -> R.string.message_burned
          },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
      )
      return@Row
    }
    Column(
      horizontalAlignment = if (message.outgoing) Alignment.End else Alignment.Start,
    ) {
    SealBubble(
      spec = message.spec,
      text = message.text,
      revealed = revealed,
      instant = instant,
      onReveal = onReveal,
      onLongPress = onLongPress,
      // Sotto la copertina, per un allegato, c'e' l'immagine o la voce invece del testo. Il
      // rituale non cambia: quello che cambia e' cosa si trova aperto il sigillo.
      body = message.media?.let { media ->
        {
          when (media.kind) {
            MessageBody.Media.Kind.PHOTO -> PhotoBody(bytes, media, onOpen = onOpenPhoto)
            MessageBody.Media.Kind.VOICE -> VoiceBody(media, bytes)
          }
        }
      },
      modifier = Modifier
        .widthIn(max = 300.dp)
        .then(if (hinted) Modifier.fluidTutorialAnchor(CodexHint.SealTap) else Modifier),
      containerColor = if (message.outgoing) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      },
      contentColor = if (message.outgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
      } else {
        MaterialTheme.colorScheme.onSurface
      },
    )
      // L'ora sta **fuori** dalla bolla, sotto.
      //
      // Dentro non ci puo' stare: la copertina di una roccia o di un quadro non ha un angolo
      // libero, e infilarcela vorrebbe dire scrivere sopra il sigillo. Fuori funziona per tutte e
      // tre le tecniche, e l'orario di un messaggio non e' il suo contenuto: dirlo su un sigillo
      // ancora chiuso non svela niente.
      Row(
        modifier = Modifier.padding(top = 3.dp, start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = remember(message.createdAt) { formatTime(message.createdAt) },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (message.outgoing) StatusMark(message.status)
      }
    }
  }
  }
}

/**
 * Dove e' arrivato un messaggio: in attesa, partito, consegnato, letto, oppure non partito.
 *
 * Piccolo e accanto all'ora, perche' e' un'informazione che si guarda solo quando si dubita.
 * Diventa dell'accento **solo a lettura avvenuta**: e' l'unico stato che interessa davvero, e
 * tingere anche gli altri toglierebbe a quello il suo momento.
 */
@Composable
private fun StatusMark(status: String) {
  val icon = when (status) {
    MessageStatus.PENDING -> Icons.Rounded.Schedule
    MessageStatus.SENT -> Icons.Rounded.Check
    MessageStatus.DELIVERED, MessageStatus.READ -> Icons.Rounded.DoneAll
    MessageStatus.FAILED -> Icons.Rounded.ErrorOutline
    else -> return
  }
  val label = when (status) {
    MessageStatus.PENDING -> R.string.status_pending
    MessageStatus.SENT -> R.string.status_sent
    MessageStatus.DELIVERED -> R.string.status_delivered
    MessageStatus.READ -> R.string.status_read
    else -> R.string.status_failed
  }
  Icon(
    imageVector = icon,
    contentDescription = stringResource(label),
    tint = when (status) {
      MessageStatus.READ -> MaterialTheme.colorScheme.primary
      MessageStatus.FAILED -> MaterialTheme.colorScheme.error
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    },
    modifier = Modifier.size(13.dp),
  )
}

/** L'ora di un messaggio, nel formato del telefono. */
private fun formatTime(at: Long): String = Instant.ofEpochMilli(at)
  .atZone(ZoneId.systemDefault())
  .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()))

/** Quanto e' larga al massimo la colonna dei messaggi. Oltre, si legge male. */
private val ChatContentWidth = 620.dp

/**
 * La barra di scrittura, appoggiata sul contenuto.
 *
 * Il tasto di invio si tiene premuto per scegliere la tecnica: un tocco manda con quella corrente
 * (di solito "a sorpresa"), e chi non scopre mai il gesto non perde niente.
 */
@Composable
private fun Composer(
  state: ComposerState,
  recording: RecordingState,
  backdrop: GlassBackdropState,
  onDraftChange: (String) -> Unit,
  onSend: () -> Unit,
  onOpenChooser: () -> Unit,
  onAttach: () -> Unit,
  onRecord: () -> Unit,
  onStopRecording: () -> Unit,
  onCancelRecording: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
  Row(
    modifier = Modifier
      .widthIn(max = ChatContentWidth + 32.dp)
      .fillMaxWidth()
      .navigationBarsPadding()
      .imePadding()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    // Mentre si registra la barra **cambia mestiere**: niente testo, niente allegati, solo la voce
    // che cresce, il tempo, e le due uscite. Lasciare tutto il resto acceso vorrebbe dire poter
    // scrivere un messaggio con il microfono aperto, e nessuna delle due cose verrebbe bene.
    if (recording.active) {
      FluidGlassIconButton(onClick = onCancelRecording, backdrop = backdrop) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = stringResource(R.string.voice_cancel),
          modifier = Modifier.size(20.dp),
        )
      }
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        RecordingWave(
          waveform = recording.waveform,
          modifier = Modifier
            .weight(1f)
            .height(26.dp),
        )
        Text(
          text = formatDuration(recording.millis),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      FluidGlassIconButton(onClick = onStopRecording, backdrop = backdrop) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Send,
          contentDescription = stringResource(R.string.voice_send),
          modifier = Modifier.size(20.dp),
        )
      }
      return@Row
    }

    FluidGlassIconButton(onClick = onAttach, backdrop = backdrop) {
      Icon(
        imageVector = Icons.Rounded.Image,
        contentDescription = stringResource(R.string.chat_attach),
        modifier = Modifier.size(20.dp),
      )
    }
    Box(modifier = Modifier.weight(1f)) {
      TastieraInIncognito {
        FluidTextField(
          value = state.draft,
          onValueChange = onDraftChange,
          placeholder = stringResource(R.string.chat_placeholder),
          singleLine = false,
          maxLines = 4,
        )
      }
    }
    // Il microfono compare **al posto** dell'invio quando non c'e' niente da mandare: e' lo stesso
    // posto, e non si impara un tasto in piu'.
    //
    // Anche lui apre la scelta della tecnica tenendolo premuto, ed e' l'unico modo per accendere
    // "visualizza una volta" **prima** di allegare una foto: con la casella vuota il tasto di invio
    // non c'e', e quella scelta sarebbe rimasta raggiungibile solo scrivendo qualcosa prima.
    if (state.draft.isBlank()) {
      FluidGlassIconButton(onClick = onRecord, backdrop = backdrop, onLongClick = onOpenChooser) {
        Icon(
          imageVector = Icons.Rounded.Mic,
          contentDescription = stringResource(R.string.voice_record),
          modifier = Modifier.size(20.dp),
        )
      }
      return@Row
    }
    FluidGlassIconButton(
      onClick = onSend,
      modifier = Modifier.fluidTutorialAnchor(CodexHint.SendHold),
      backdrop = backdrop,
      // Spento quando non c'e' niente da mandare. Un tasto che si puo' premere e non fa niente
      // insegna a non fidarsi dei tasti.
      enabled = !state.sending,
      // La pressione lunga va data al bottone, non a un `fluidPressable` messo sopra: dentro il
      // bottone c'e' gia' un rilevatore di gesti, e quello esterno non vede mai niente. Il primo
      // tentativo era esattamente quello, e la pressione lunga mandava il messaggio.
      onLongClick = onOpenChooser,
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.Send,
        contentDescription = stringResource(R.string.chat_send),
        modifier = Modifier.size(20.dp),
      )
    }
  }
  }
}

/**
 * La voce che cresce mentre si registra.
 *
 * Serve a una cosa sola, ed e' importante: far vedere che il microfono **sta sentendo**. Una barra
 * ferma durante una registrazione e' indistinguibile da un'app bloccata, e chi la vede riprova da
 * capo parlando piu' forte.
 */
@Composable
private fun RecordingWave(waveform: ByteArray, modifier: Modifier = Modifier) {
  val colore = MaterialTheme.colorScheme.primary
  Canvas(modifier = modifier) {
    if (waveform.isEmpty()) return@Canvas
    val spazio = size.width / waveform.size
    val larghezza = (spazio * 0.55f).coerceAtLeast(1.5f)
    waveform.forEachIndexed { indice, valore ->
      val altezza = size.height * (0.15f + 0.85f * (valore.toInt() and 0xFF) / 255f)
      drawRoundRect(
        color = colore,
        topLeft = Offset(indice * spazio + (spazio - larghezza) / 2f, (size.height - altezza) / 2f),
        size = Size(larghezza, altezza),
        cornerRadius = CornerRadius(larghezza / 2f),
      )
    }
  }
}

/** La scelta della tecnica: un pannello di vetro con le quattro possibilita'. */
@Composable
private fun TechniqueChooser(
  open: Boolean,
  selected: Technique?,
  viewOnce: Boolean,
  onDismiss: () -> Unit,
  onSelect: (Technique?) -> Unit,
  onViewOnce: (Boolean) -> Unit,
) {
  val options: List<Pair<Technique?, Pair<String, String>>> = listOf(
    null to (stringResource(R.string.technique_surprise) to stringResource(R.string.technique_surprise_detail)),
    Technique.RUNE to (stringResource(R.string.technique_rune) to stringResource(R.string.technique_rune_detail)),
    Technique.ROCK to (stringResource(R.string.technique_rock) to stringResource(R.string.technique_rock_detail)),
    Technique.PAINTING to (stringResource(R.string.technique_painting) to stringResource(R.string.technique_painting_detail)),
  )

  FluidGlassModalPortal(
    visible = open,
    onDismissRequest = onDismiss,
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = stringResource(R.string.technique_title),
  ) {
    CodexSheetColumn(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
    ) {
      FluidListGroup {
        options.forEachIndexed { index, (technique, labels) ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = labels.first,
            subtitle = labels.second,
            meta = if (technique == selected) stringResource(R.string.technique_current) else null,
            onClick = { onSelect(technique) },
          )
        }
      }
      // Vale per il **prossimo** messaggio e si spegne da sola dopo l'invio: e' una decisione su
      // una cosa che si sta per scrivere, non un'impostazione della conversazione.
      FluidListGroup {
        FluidListRow(
          title = stringResource(R.string.view_once),
          subtitle = stringResource(R.string.view_once_detail),
          onClick = { onViewOnce(!viewOnce) },
          badge = {
            FluidSwitch(checked = viewOnce, onCheckedChange = onViewOnce)
          },
        )
      }
    }
  }
}

/** Le voci che stanno prima dei messaggi nella lista: il titolo grande della schermata. */
private const val HEADER_ITEMS = 1

/**
 * Lo spazio che la barra di scrittura si prende dal contenuto.
 *
 * Da quando sotto ogni bolla c'e' anche la riga dell'ora e della spunta, ottantotto non bastavano
 * piu': l'ultimo messaggio finiva sotto il tasto di invio.
 */
private val ComposerReservedHeight = 104.dp

/**
 * Quello che si scrive qui dentro **non deve essere imparato dalla tastiera**.
 *
 * Un'app che cifra i messaggi e poi lascia che le parole di quei messaggi ricompaiano come
 * suggerimenti in un'altra app ha protetto il tubo e lasciato aperta la stanza. Il nome di una
 * persona, un indirizzo, una parola d'ordine detta per sbaglio: il dizionario personale della
 * tastiera li tiene, e li mostra altrove.
 *
 * `IME_FLAG_NO_PERSONALIZED_LEARNING` esiste dal 2018 apposta per questo -- e' cio' che accende la
 * modalita' incognito di Gboard -- ma Compose non lo espone: `KeyboardOptions` non ha un campo per
 * i flag dell'IME. L'unico modo vero e' mettere le mani sull'`EditorInfo` mentre viene costruito,
 * ed e' quello che fa questo involucro.
 *
 * Resta una **richiesta**: una tastiera che decida di ignorarla lo puo' fare, e da qui non c'e' modo
 * di accorgersene.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TastieraInIncognito(content: @Composable () -> Unit) {
  InterceptPlatformTextInput(
    interceptor = { request, nextHandler ->
      val conIncognito = object : PlatformTextInputMethodRequest {
        override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
          val connection = request.createInputConnection(outAttributes)
          outAttributes.imeOptions =
            outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
          return connection
        }
      }
      nextHandler.startInputMethod(conIncognito)
    },
    content = content,
  )
}
