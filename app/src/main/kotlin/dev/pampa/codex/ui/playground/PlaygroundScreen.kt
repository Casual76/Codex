package dev.pampa.codex.ui.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.pampa.codex.model.SealChoice
import dev.pampa.codex.model.Technique
import dev.pampa.codex.seal.MineralGenerator
import dev.pampa.codex.seal.PaintingCatalog
import dev.pampa.codex.seal.PaintingStego
import dev.pampa.codex.seal.SealResolver
import dev.pampa.codex.ui.seal.MineralView
import dev.pampa.codex.ui.seal.PaintingSealSurface
import dev.pampa.codex.ui.seal.SealBubble

/**
 * Il banco di prova dei sigilli. Esiste solo nelle build di lavoro.
 *
 * Serve a una cosa che nessun test automatico sa fare: **guardare**. Le animazioni di apertura si
 * giudicano a occhio e con i fotogrammi, e farlo dentro una chat vera vorrebbe dire creare un
 * contatto, mandarsi un messaggio e aspettare la rete ogni volta che si sposta una costante.
 *
 * Qui si cambia testo, tecnica e seme, e si riapre lo stesso sigillo quante volte serve.
 */
@Composable
fun PlaygroundScreen(onBack: () -> Unit) {
  var text by remember {
    mutableStateOf("Ci vediamo alle sei sotto il portico, porta il libro che ti ho detto.")
  }
  var techniqueIndex by remember { mutableIntStateOf(0) }
  var seed by remember { mutableLongStateOf(20260907L) }
  var revealed by remember { mutableStateOf(false) }
  var stegoReport by remember { mutableStateOf<String?>(null) }

  val options = listOf<Technique?>(null, Technique.RUNE, Technique.ROCK, Technique.PAINTING)
  val labels = listOf("Sorpresa", "Rune", "Roccia", "Quadro")
  val choice = SealChoice(technique = options[techniqueIndex])
  val spec = remember(seed, techniqueIndex) { SealResolver.resolve(seed, choice) }

  val context = LocalContext.current
  val catalog = remember(context) { PaintingCatalog(context.assets) }

  FluidScreen(
    title = "Playground",
    subtitle = "I sigilli, senza una chat intorno.",
    onBack = onBack,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.SecondaryToTertiary, motif = FluidHeroMotif.Ripples) },
  ) {
    item(key = "message") {
      FluidCard(glass = true) {
        FluidTextField(
          value = text,
          onValueChange = { text = it },
          label = "Il messaggio",
          singleLine = false,
          minLines = 2,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
    item(key = "technique") {
      FluidCard(glass = true) {
        FluidSegmentedControl(
          options = labels,
          selected = labels[techniqueIndex],
          onSelect = { label ->
            techniqueIndex = labels.indexOf(label)
            revealed = false
          },
          label = { it },
        )
      }
    }
    item(key = "seal") {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(280.dp),
        contentAlignment = Alignment.Center,
      ) {
        SealBubble(
          spec = spec,
          text = text,
          revealed = revealed,
          onReveal = { revealed = true },
        )
      }
    }
    item(key = "controls") {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FluidButton(
          text = if (revealed) "Risigilla" else "Apri",
          onClick = { revealed = !revealed },
        )
        FluidButton(
          text = "Nuovo seme",
          onClick = {
            seed += 1
            revealed = false
          },
          style = FluidButtonStyle.Tinted,
        )
      }
    }
    item(key = "stego") {
      // La prova del giro completo: il messaggio entra in un dipinto, il dipinto diventa un PNG, il
      // PNG viene riletto da zero. E' il criterio di accettazione del piano, e farlo qui significa
      // poterlo rifare in due secondi ogni volta che si tocca qualcosa.
      FluidButton(
        text = "Prova andata e ritorno nel dipinto",
        onClick = {
          val painting = catalog.byId(spec.paintingId) ?: catalog.pick(spec.seed)
          stegoReport = if (painting == null) {
            "nessun dipinto nella raccolta"
          } else {
            runCatching {
              val stego = PaintingStego(catalog)
              val payload = text.toByteArray()
              val bitmap = stego.embed(painting, payload)
              val buffer = java.io.ByteArrayOutputStream()
              stego.writePng(bitmap, buffer)
              val bytes = buffer.toByteArray()
              val recovered = stego.extract(java.io.ByteArrayInputStream(bytes))
              val ok = recovered != null && recovered.contentEquals(payload)
              val kilobytes = bytes.size / 1024
              if (ok) {
                "andata e ritorno riuscita: ${payload.size} byte in ${painting.title}, PNG da $kilobytes KB"
              } else {
                "FALLITA: il messaggio riletto non coincide"
              }
            }.getOrElse { "FALLITA: ${it.message}" }
          }
        },
        style = FluidButtonStyle.Tinted,
      )
    }
    if (stegoReport != null) {
      item(key = "stego-report") { FluidSectionFootnote(text = stegoReport.orEmpty()) }
    }
    item(key = "spec") {
      // La scala del movimento e' qui apposta: se il sistema la mette a zero le animazioni
      // decorative spariscono, e senza vederla scritta si finisce a cercare il difetto nel codice
      // dell'animazione invece che nelle impostazioni del telefono.
      val motion = LocalFluidMotionPolicy.current
      FluidSectionFootnote(
        text = "Tecnica ${spec.technique}, seme ${spec.seed}" +
          (spec.paintingId?.let { ", dipinto $it" } ?: "") +
          " · scala movimento ${motion.durationScale}",
      )
    }

    item(key = "minerals-header") { FluidSectionHeader(title = "Minerali") }
    item(key = "minerals") {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(24) { index ->
          MineralView(
            mineral = remember(index, seed) { MineralGenerator.generate(seed + index * 7919L) },
            modifier = Modifier.size(64.dp),
          )
        }
      }
    }

    item(key = "paintings-header") {
      FluidSectionHeader(
        title = "Dipinti",
        detail = "${catalog.paintings.size} opere di pubblico dominio incluse nell'APK",
      )
    }
    item(key = "paintings") {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(catalog.paintings.size) { index ->
          val painting = catalog.paintings[index]
          Column(modifier = Modifier.width(148.dp)) {
            PaintingSealSurface(
              painting = painting,
              seed = index.toLong(),
              progress = { 0f },
              modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            )
            Text(
              text = painting.title,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
            )
          }
        }
      }
    }

    item(key = "cloud-header") { FluidSectionHeader(title = "Rubrica") }
    item(key = "cloud") { CloudProbe() }
  }
}

/**
 * Le tre prove del livello cloud: entra, pubblica, cerca.
 *
 * Sta nel Playground e non altrove perche' e' l'unico posto dell'app che esiste solo nelle build di
 * lavoro. Serve a far **girare davvero** il codice che parla con Firestore: le finte dei test
 * provano cosa succede quando il server non risponde, non che la chiamata vera funzioni.
 */
@Composable
private fun CloudProbe(viewModel: CloudProbeViewModel = hiltViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  var codexId by remember { mutableStateOf("") }

  FluidCard(glass = true) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = if (state.emulator.isBlank()) {
          "Progetto vero. L'accesso anonimo qui e' rifiutato apposta."
        } else {
          "Emulatore Firebase su ${state.emulator}"
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = state.account,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidButton(
          text = "Entra",
          style = FluidButtonStyle.Tinted,
          enabled = !state.busy,
          onClick = viewModel::signIn,
        )
        FluidButton(
          text = "Pubblica",
          style = FluidButtonStyle.Tinted,
          enabled = !state.busy,
          onClick = viewModel::publish,
        )
      }
      FluidTextField(
        value = codexId,
        onValueChange = { codexId = it },
        label = "Cerca un Codex ID",
        placeholder = "CDX-7K3P-2M9Q",
      )
      FluidButton(
        text = "Cerca",
        enabled = !state.busy && codexId.isNotBlank(),
        loading = state.busy,
        fillWidth = true,
        onClick = { viewModel.lookup(codexId) },
      )
      if (state.log.isNotBlank()) {
        Text(
          text = state.log,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}
