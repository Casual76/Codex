package dev.pampa.codex.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTabRail
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.pampa.codex.R
import dev.pampa.codex.ui.chat.ChatInfoScreen
import dev.pampa.codex.ui.chat.ChatScreen
import dev.pampa.codex.ui.chats.ChatsScreen
import dev.pampa.codex.ui.contacts.AddContactScreen
import dev.pampa.codex.ui.contacts.ContactsScreen
import dev.pampa.codex.ui.contacts.MySealScreen
import dev.pampa.codex.ui.groups.JoinGroupScreen
import dev.pampa.codex.ui.groups.NewGroupScreen
import dev.pampa.codex.ui.contacts.RitualScreen
import dev.pampa.codex.ui.me.CreditsScreen
import dev.pampa.codex.ui.me.MeScreen
import dev.pampa.codex.ui.origins.OriginsScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import dev.pampa.codex.ui.groups.GroupInviteViewModel
import dev.pampa.codex.ui.paintings.PaintingImportHost
import dev.pampa.codex.ui.playground.PlaygroundScreen
import dev.pampa.codex.ui.stories.StoriesScreen
import dev.pampa.codex.ui.tutorial.CodexHintState
import dev.pampa.codex.ui.tutorial.CodexHintsViewModel
import dev.pampa.codex.ui.tutorial.LocalCodexHints
import dev.pampa.codex.ui.tutorial.codexHintLabels
import dev.pampa.codex.ui.theme.LocalCodexPaneWidth
import dev.antigravity.fluidengine.ui.tutorial.FluidTutorialHost
import dev.antigravity.fluidengine.ui.tutorial.LocalFluidTutorialHostState
import dev.antigravity.fluidengine.ui.tutorial.fluidTutorialTouches
import dev.antigravity.fluidengine.ui.tutorial.rememberFluidTutorialHostState

/**
 * La radice di Codex, nella forma che un'app vera ha.
 *
 * La barra in vetro, l'host delle notifiche, quello dei modali e quello dei menu che si
 * trasformano sono tutti fratelli del contenuto e leggono tutti `chromeController.activeBackdrop`:
 * e' cosi' che il vetro rifrange la pagina che ha davvero sotto, ed e' lo stesso cablaggio di
 * ClasseViva, Pampa Store e della galleria dell'engine.
 */
@Composable
fun CodexRoot(
  settings: EngineSettings,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDynamicColorChange: (Boolean) -> Unit,
  onAmoledChange: (Boolean) -> Unit,
  onHapticsChange: (Boolean) -> Unit,
) {
  val tabItems = listOf(
    FluidTabItem(route = CodexRoutes.Chats, label = stringResource(R.string.tab_chats), icon = Icons.Rounded.Forum),
    FluidTabItem(route = CodexRoutes.Stories, label = stringResource(R.string.tab_stories), icon = Icons.Rounded.AutoAwesome),
    FluidTabItem(route = CodexRoutes.Me, label = stringResource(R.string.tab_me), icon = Icons.Rounded.Person),
  )

  val navController = rememberNavController()
  val chromeController = rememberFluidChromeController()
  val fallbackBackdrop = rememberGlassBackdrop()
  val backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop
  val scrollToTop = remember { FluidScrollToTopBus() }
  val modalHost = rememberFluidGlassModalHostState()
  val notificationHost = rememberFluidNotificationHostState()
  val morphMenu = rememberFluidMorphMenuState()

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = backStackEntry?.destination
  val selectedRoute = tabItems.firstOrNull { currentDestination.isInHierarchy(it.route) }?.route

  // I suggerimenti al primo uso. Il padrone di casa sta qui perche' il callout deve poter uscire
  // dai bordi della schermata che lo ha chiesto -- e perche' la coda si svuota da sola quando si
  // cambia pagina, cosa che solo la radice sa quando succede.
  val hintsViewModel = hiltViewModel<CodexHintsViewModel>()
  val hintsSaved by hintsViewModel.state.collectAsStateWithLifecycle()
  val tutorialState = rememberFluidTutorialHostState()
  val hintLabels = codexHintLabels()
  val currentRoute = currentDestination?.route.orEmpty()
  // Le due chiamate di ritorno si riagganciano fuori dalla composizione: scriverle dentro
  // significherebbe farlo anche durante una composizione buttata via.
  SideEffect {
    tutorialState.onShown = hintsViewModel::markSeen
    tutorialState.onDismissed = { _, optOut -> if (optOut) hintsViewModel.silence() }
  }
  val hints = remember(currentRoute, hintsSaved) { hintsSaved.copy(screen = currentRoute) }

  CompositionLocalProvider(
    LocalFluidGlassModalHostState provides modalHost,
    LocalFluidNotificationHostState provides notificationHost,
    LocalFluidTutorialHostState provides tutorialState,
    LocalCodexHints provides hints,
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      // In orizzontale la barra in basso mangia l'altezza, che e' proprio quella che manca. Sopra
      // questa soglia la stessa navigazione si mette di lato: e' la variante che l'engine gia' ha,
      // stesso materiale e stessa lente, in piedi.
      val wide = maxWidth >= RailBreakpoint
      // Piu' larga ancora: la lista non se ne va piu' quando si entra in una conversazione, si
      // mette di fianco. La soglia e' piu' alta di quella della guida laterale perche' un telefono
      // in orizzontale supera i 600 dp ma non ha spazio per due colonne: due pannelli da 300 dp
      // sono due pannelli scomodi al posto di uno comodo.
      val twoPane = maxWidth >= TwoPaneBreakpoint
      val onChat = currentDestination?.route == CodexRoutes.Chat
      // I due pannelli valgono per le conversazioni e basta: le altre pagine spinte (contatti,
      // gruppo nuovo, le impostazioni di una chat) restano schermate intere.
      val chatArea = twoPane && (onChat || currentDestination?.route == CodexRoutes.Chats)
      val openChatId = backStackEntry?.arguments?.getString("chatId").takeIf { onChat }
      ProvideFluidChrome(
        controller = chromeController,
        // Lo spazio che la barra occuperebbe torna al contenuto come spaziatura in fondo: le liste
        // finiscono sopra la barra invece che dietro. Con la guida di lato non serve.
        bottomInset = if (wide) 0.dp else FluidTabBarDefaults.ContentInset,
        scrollToTop = scrollToTop,
      ) {
        Row(modifier = Modifier.fillMaxSize()) {
          if (wide && (!onChat || twoPane)) {
            FluidTabRail(
              items = tabItems,
              selectedRoute = selectedRoute,
              onSelect = { item -> navController.navigateToTab(item.route) },
              onReselect = { scrollToTop.request() },
              backdrop = backdrop,
              modifier = Modifier
                .fillMaxHeight()
                .safeDrawingPadding()
                .padding(start = 8.dp),
            )
          }
        if (chatArea) {
          Box(modifier = Modifier.width(ChatListPaneWidth).fillMaxHeight()) {
            // Il pannello dice quanto e' largo: senza, la lista calcola i propri margini sullo
            // schermo intero e si strozza da sola.
            CompositionLocalProvider(LocalCodexPaneWidth provides ChatListPaneWidth) {
            ChatsScreen(
              onOpenChat = { chatId ->
                // Da un pannello all'altro non si impila: aprire una seconda conversazione
                // **sostituisce** la prima, altrimenti il tasto indietro ripercorrerebbe a ritroso
                // tutte quelle guardate, che su un telefono ha senso e affiancate non ne ha.
                navController.navigate(CodexRoutes.chat(chatId)) {
                  popUpTo(CodexRoutes.Chats)
                  launchSingleTop = true
                }
              },
              onOpenContacts = { navController.navigate(CodexRoutes.Contacts) },
              onNewGroup = { navController.navigate(CodexRoutes.NewGroup) },
              onJoinGroup = { navController.navigate(CodexRoutes.JoinGroup) },
              selectedChatId = openChatId,
            )
            }
          }
        }
        // L'osservatore dei tocchi sta sul contenitore della pagina, mai sopra: da genitore vede
        // il dito senza consumarlo, e quello che c'e' sotto continua a rispondere.
        BoxWithConstraints(
          modifier = Modifier
            // `weight` e non `fillMaxSize`: dentro una Row quest'ultimo si prende tutta la
            // larghezza in un colpo, e al pannello della lista -- che ha una misura fissa e viene
            // misurato dopo -- non resta niente. Si vedeva: una colonna di lettere in verticale.
            .weight(1f)
            .fillMaxHeight()
            .fluidTutorialTouches(tutorialState)
            .fluidGlassModalObscured(),
        ) {
          // Quanto e' largo davvero questo pannello: misurato, non dedotto. La guida di lato e il
          // pannello della lista se ne prendono un pezzo, e nessuno dei due lo sa in anticipo.
          CompositionLocalProvider(LocalCodexPaneWidth provides maxWidth) {
          LaunchedEffect(currentRoute) { tutorialState.screenChanged(currentRoute) }
          NavHost(
            navController = navController,
            startDestination = CodexRoutes.Chats,
          ) {
            composable(CodexRoutes.Chats) {
              if (chatArea) {
                // La lista e' gia' a sinistra: qui ci va quello che manca, cioe' una conversazione
                // scelta. Rimetterci la stessa lista vorrebbe dire mostrarla due volte.
                Box(
                  modifier = Modifier.fillMaxSize().padding(24.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  FluidEmptyState(
                    title = stringResource(R.string.chats_pane_empty),
                    detail = stringResource(R.string.chats_pane_empty_detail),
                  )
                }
              } else {
                ChatsScreen(
                  onOpenChat = { chatId -> navController.navigate(CodexRoutes.chat(chatId)) },
                  onOpenContacts = { navController.navigate(CodexRoutes.Contacts) },
                  onNewGroup = { navController.navigate(CodexRoutes.NewGroup) },
                  onJoinGroup = { navController.navigate(CodexRoutes.JoinGroup) },
                )
              }
            }
            composable(CodexRoutes.NewGroup) {
              NewGroupScreen(
                onBack = { navController.popBackStack() },
                // Creato il gruppo ci si entra dentro: e' quello che uno vuole fare subito dopo. La
                // schermata che l'ha creato esce dalla pila -- tornare indietro a un modulo gia'
                // compilato non serve a nessuno.
                onCreated = { chatId ->
                  navController.navigate(CodexRoutes.chat(chatId)) {
                    popUpTo(CodexRoutes.NewGroup) { inclusive = true }
                  }
                },
              )
            }
            composable(CodexRoutes.JoinGroup) {
              JoinGroupScreen(onBack = { navController.popBackStack() })
            }
            composable(CodexRoutes.Contacts) {
              ContactsScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate(CodexRoutes.chat(chatId)) },
                onMySeal = { navController.navigate(CodexRoutes.MySeal) },
                onAdd = { navController.navigate(CodexRoutes.AddContact) },
                onRitual = { codexId -> navController.navigate(CodexRoutes.ritual(codexId)) },
              )
            }
            composable(CodexRoutes.MySeal) {
              MySealScreen(onBack = { navController.popBackStack() })
            }
            composable(CodexRoutes.AddContact) {
              AddContactScreen(
                onBack = { navController.popBackStack() },
                // La scheda e' entrata: da qui si va al rito, e tornare indietro deve riportare ai
                // contatti, non alla fotocamera che ha appena finito il suo lavoro.
                onRitual = { codexId ->
                  navController.navigate(CodexRoutes.ritual(codexId)) {
                    popUpTo(CodexRoutes.AddContact) { inclusive = true }
                  }
                },
              )
            }
            composable(
              route = CodexRoutes.Ritual,
              arguments = listOf(navArgument("codexId") { type = NavType.StringType }),
            ) {
              RitualScreen(
                onBack = { navController.popBackStack() },
                // Entrare nella conversazione chiude il giro del pairing: la pila torna ai
                // contatti, cosi' il tasto indietro dalla chat non ripropone il rito appena fatto.
                onOpenChat = { chatId ->
                  navController.navigate(CodexRoutes.chat(chatId)) {
                    popUpTo(CodexRoutes.Contacts)
                  }
                },
              )
            }
            composable(
              route = CodexRoutes.Chat,
              arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            ) { entry ->
              val chatId = entry.arguments?.getString("chatId").orEmpty()
              ChatScreen(
                onBack = { navController.popBackStack() },
                onOpenInfo = { navController.navigate(CodexRoutes.chatInfo(chatId)) },
              )
            }
            composable(
              route = CodexRoutes.ChatInfo,
              arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
            ) {
              ChatInfoScreen(
                onBack = { navController.popBackStack() },
                // Svuotata la chat non ha senso restare sulle sue impostazioni: si torna alla
                // conversazione, che ora e' vuota.
                onCleared = { navController.popBackStack() },
              )
            }
            composable(CodexRoutes.Stories) { StoriesScreen() }
            composable(CodexRoutes.Me) {
              MeScreen(
                settings = settings,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onAmoledChange = onAmoledChange,
                onHapticsChange = onHapticsChange,
                onOpenOrigins = { navController.navigate(CodexRoutes.Origins) },
                onOpenCredits = { navController.navigate(CodexRoutes.Credits) },
                onOpenPlayground = { navController.navigate(CodexRoutes.Playground) },
              )
            }
            composable(CodexRoutes.Origins) {
              OriginsScreen(onBack = { navController.popBackStack() })
            }
            composable(CodexRoutes.Credits) {
              CreditsScreen(onBack = { navController.popBackStack() })
            }
            composable(CodexRoutes.Playground) {
              PlaygroundScreen(onBack = { navController.popBackStack() })
            }
          }
        }
          }
        }
      }

      // Dentro una conversazione la barra sparisce: e' una pagina spinta, e la scrittura si prende
      // tutto lo spazio in fondo. In orizzontale la navigazione sta gia' di lato, dentro la Row.
      if (!wide && !onChat) {
        FluidTabBar(
          items = tabItems,
          selectedRoute = selectedRoute,
          onSelect = { item -> navController.navigateToTab(item.route) },
          onReselect = { scrollToTop.request() },
          backdrop = backdrop,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }

      // Sopra la barra, ed e' tutto il motivo per cui i modali stanno alla radice: un pop-up su cui
      // una capsula di navigazione puo' posarsi non e' un modale.
      // Il quadro che arriva da fuori puo' arrivare in qualunque momento: il pannello che lo
      // annuncia sta qui, sopra qualsiasi schermata.
      PaintingImportHost(
        onOpenChat = { chatId -> navController.navigate(CodexRoutes.chat(chatId)) },
      )

      // Un invito toccato da fuori porta alla schermata che lo sa leggere, e la trova gia' piena.
      // Non entra in nessun gruppo: chiedere di entrare resta un gesto di chi apre l'app.
      val invito by hiltViewModel<GroupInviteViewModel>().pending.collectAsStateWithLifecycle()
      LaunchedEffect(invito) {
        if (invito != null) navController.navigate(CodexRoutes.JoinGroup)
      }

      // Sopra la pagina e sotto i pannelli: un suggerimento non e' un modale, e non deve mai
      // trovarsi davanti a una domanda che aspetta una risposta.
      FluidTutorialHost(
        state = tutorialState,
        labels = hintLabels,
        backdrop = backdrop,
        modalPresenting = { modalHost.isPresenting },
      )

      FluidGlassModalHost(state = modalHost, backdrop = backdrop)
      FluidMorphMenuHost(state = morphMenu, backdrop = backdrop)
      FluidNotificationHost(
        state = notificationHost,
        backdrop = backdrop,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }
  }
}

/** Cambiare scheda senza impilarle: la stessa regola per la barra e per la guida di lato. */
private fun androidx.navigation.NavHostController.navigateToTab(route: String) {
  navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
  }
}

/** Sopra questa larghezza la navigazione passa di lato. E' anche un telefono in orizzontale. */
private val RailBreakpoint: Dp = 600.dp

/** Sopra questa, la lista e la conversazione stanno insieme. E' un tablet, o un pieghevole aperto. */
private val TwoPaneBreakpoint: Dp = 840.dp

/** Quanto e' larga la lista quando sta di fianco: abbastanza per un nome e un'ora, non di piu'. */
private val ChatListPaneWidth: Dp = 340.dp

private fun NavDestination?.isInHierarchy(route: String): Boolean =
  this?.hierarchy?.any { it.route == route || it.route?.startsWith("$route/") == true } == true
