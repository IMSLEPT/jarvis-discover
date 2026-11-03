package it.jarvis.discover.ui.wellbeing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import it.jarvis.discover.data.wellbeing.Activity
import it.jarvis.discover.data.wellbeing.WhPhase
import it.jarvis.discover.data.wellbeing.WimHof
import it.jarvis.discover.data.wellbeing.WimHofPlayer
import it.jarvis.discover.data.wellbeing.WimHofRun
import it.jarvis.discover.ui.components.BrandBackground
import it.jarvis.discover.ui.components.EmojiBubble
import it.jarvis.discover.ui.components.ScreenPadding
import it.jarvis.discover.ui.components.SoftCard
import it.jarvis.discover.ui.components.openUrl

/**
 * Metodo Wim Hof come allenamento guidato di respirazione, con un controllo di sicurezza obbligatorio prima di
 * ogni sessione. Il freddo è trattato solo come informazione: non c'è nessun esercizio con il freddo nell'app.
 */
@Composable
fun WimHofCard(player: WimHofPlayer, entries: List<Activity>, onBeforeStart: () -> Unit) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var levelId by rememberSaveable { mutableStateOf(WimHof.GENTLE_ID) }
    var gate by remember { mutableStateOf(false) }
    val level = WimHof.byId(levelId)?.takeIf { WimHof.unlocked(it, entries) } ?: WimHof.levels.first()

    SoftCard(modifier = Modifier.animateContentSize(), contentPadding = PaddingValues(0.dp)) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                EmojiBubble(emoji = "❄️", size = 44, background = MaterialTheme.colorScheme.secondaryContainer)
                Column(Modifier.weight(1f)) {
                    Text("Metodo Wim Hof: allenamento guidato", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text("Respirazione guidata, con controlli di sicurezza prima di iniziare.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Info(
                        "Che cos'è",
                        "Un metodo che combina respirazione intensa, esposizione al freddo e concentrazione, reso popolare da Wim Hof. " +
                            "Qui trovi solo la parte di respirazione, in una versione prudente. Non è una cura e non sostituisce il parere del medico.",
                    )

                    Info(
                        "Come si fa",
                        "Siediti o sdraiati su un divano, un letto o un tappeto, in un posto sicuro. Ogni giro ha quattro momenti:\n" +
                            "1. Respiri profondi: inspira a fondo ed espira senza forzare, ${WimHof.levels[0].breaths}–${WimHof.levels[1].breaths} volte.\n" +
                            "2. Apnea a polmoni vuoti: dopo l'ultima espirazione smetti di respirare e aspetta, rilassato, finché senti il primo vero bisogno di respirare. Non è una gara: tocca il pulsante appena lo senti. L'app ti ferma comunque al tempo massimo del livello.\n" +
                            "3. Respiro di recupero: un respiro profondo, trattenuto 15 secondi.\n" +
                            "4. Espira e riparti con il giro successivo: sono ${WimHof.levels.first().rounds} giri in tutto.",
                    )

                    Info(
                        "Cosa può succedere e quando fermarti",
                        "Formicolii e una sensazione di testa leggera possono capitare durante i respiri profondi. " +
                            "Se diventano forti, se hai capogiri, vista offuscata, dolore o oppressione al petto, battito che non ti sembra normale, " +
                            "o ti senti strano: premi \"Fermati\" e respira normalmente. Svenire significa che si è esagerato (lo dice anche il sito ufficiale): " +
                            "la volta dopo fai meno respiri e apnee più brevi.",
                    )

                    Info(
                        "Quando NON farlo",
                        "• In gravidanza, o se hai epilessia o hai avuto crisi convulsive.\n" +
                            "• Se hai problemi di cuore o circolazione, hai avuto un ictus, hai la pressione alta o bassa, asma o altri problemi respiratori, o prendi farmaci: prima parlane con il medico.\n" +
                            "• Mai in acqua o vicino all'acqua (vasca, doccia, piscina, mare, lago), né prima o durante immersioni, nuoto o bagno.\n" +
                            "• Mai alla guida o in qualsiasi situazione in cui uno svenimento potrebbe farti male, e mai in piedi.",
                    )

                    Info(
                        "E il freddo?",
                        "Nell'app non c'è nessun esercizio con il freddo, per scelta. Il sito ufficiale dice di aumentare l'esposizione al freddo gradualmente, " +
                            "sia come temperatura sia come durata, ascoltando il corpo: la pratica irresponsabile può avere conseguenze serie, ipotermia compresa. " +
                            "Gli esperti avvertono che respiri forzati più acqua fredda sono la combinazione più pericolosa, con rischio di annegamento e di problemi del ritmo del cuore. " +
                            "Se vuoi provare il freddo, parlane prima con il medico e non farlo mai subito dopo gli esercizi di respirazione né da solo in acque aperte.",
                    )

                    Info(
                        "Cosa dicono gli studi",
                        "Le ricerche sono poche, piccole e di qualità giudicata molto bassa: una revisione degli studi dal 2014 a luglio 2022 ha trovato 8 sperimentazioni con 15–48 partecipanti, in gran parte uomini. " +
                            "Il segnale più interessante riguarda l'infiammazione, ma gli autori invitano alla cautela. Non è dimostrato come cura di alcuna malattia, e altri benefici attribuiti al metodo, come più energia o forza di volontà, non hanno prove scientifiche.",
                    )

                    Text("Scegli il livello", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(WimHof.levels, key = { it.id }) { l ->
                            val ok = WimHof.unlocked(l, entries)
                            FilterChip(
                                selected = l.id == level.id,
                                enabled = ok,
                                onClick = { levelId = l.id },
                                label = { Text(if (ok) l.title else "🔒 ${l.title}") },
                                shape = CircleShape,
                            )
                        }
                    }
                    Text(level.about, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val fixedSec = WimHof.fixedSeconds(level)
                    val maxSec = fixedSec + level.rounds * level.maxHoldSec
                    Text(
                        "Durata: da circa ${fixedSec / 60} a ${(maxSec + 59) / 60} minuti, a seconda delle tue apnee.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(onClick = { gate = true }, shape = CircleShape, modifier = Modifier.height(48.dp)) {
                        Text("Inizia l'allenamento", style = MaterialTheme.typography.titleSmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { openUrl(context, "https://wimhofmethod.com/safety-warnings") }) { Text("Avvertenze ufficiali") }
                        TextButton(onClick = { openUrl(context, "https://livescience.com/health/gambling-with-your-life-experts-weigh-in-on-dangers-of-the-wim-hof-method") }) { Text("Parere degli esperti") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { openUrl(context, "https://www.healthline.com/health/wim-hof-method") }) { Text("Chi dovrebbe evitarlo") }
                        TextButton(onClick = { openUrl(context, "https://pmc.ncbi.nlm.nih.gov/articles/PMC10936795/") }) { Text("Revisione degli studi") }
                    }
                }
            }
        }
    }

    if (gate) {
        SafetyGate(
            onCancel = { gate = false },
            onConfirm = {
                gate = false
                onBeforeStart()
                player.start(level)
            },
        )
    }
    if (player.run != null) WimHofDialog(player)
}

/** Controllo di sicurezza: si può iniziare solo spuntando tutte le voci, ogni volta. */
@Composable
private fun SafetyGate(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val items = listOf(
        "Non sono in gravidanza e non ho epilessia né ho mai avuto crisi convulsive.",
        "Non ho problemi di cuore o circolazione, ictus, pressione alta o bassa, asma o altri problemi respiratori, e non prendo farmaci regolari: oppure ne ho parlato con il mio medico e mi ha dato il via libera.",
        "Sono seduto o sdraiato su una superficie morbida e sicura, in un posto dove non cadrei da nessuna parte.",
        "Non sono in acqua né vicino all'acqua (vasca, doccia, piscina, mare) e non sto guidando.",
        "So che devo fermarmi subito se ho capogiri, vista offuscata, dolore al petto o mi sento strano. Non cerco di battere nessun record.",
    )
    val checked = remember { mutableStateOf(List(items.size) { false }) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Controllo di sicurezza") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Questa pratica può far girare la testa o addirittura far svenire. Spunta tutte le voci per iniziare.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                items.forEachIndexed { i, text ->
                    Row(
                        Modifier.fillMaxWidth().clickable { checked.value = checked.value.toMutableList().also { it[i] = !it[i] } },
                        verticalAlignment = Alignment.Top,
                    ) {
                        Checkbox(
                            checked = checked.value[i],
                            onCheckedChange = { v -> checked.value = checked.value.toMutableList().also { it[i] = v } },
                        )
                        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = checked.value.all { it }) { Text("Inizia") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annulla") } },
    )
}

/** Schermata guidata a tutto schermo. Il pulsante per fermarsi è sempre visibile. */
@Composable
private fun WimHofDialog(player: WimHofPlayer) {
    val run = player.run ?: return
    Dialog(
        onDismissRequest = player::stop,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        BrandBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = ScreenPadding, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Wim Hof · ${run.level.title}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = player::stop) { Icon(Icons.Rounded.Close, contentDescription = "Ferma e chiudi") }
                }
                if (run.finished) {
                    Finished(run, onClose = player::stop)
                } else {
                    Running(run, onEndHold = player::endHold, onStop = player::stop)
                }
            }
        }
    }
}

@Composable
private fun Running(run: WimHofRun, onEndHold: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                "Giro ${run.round} di ${run.level.rounds}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Disc(run.scale)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(run.phase.label, style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp))
                    Text("${run.secondsShown}", style = MaterialTheme.typography.headlineLarge, color = Color.White.copy(alpha = 0.95f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when (run.phase) {
                    WhPhase.Inhale, WhPhase.Exhale -> "Respiro ${run.breath} di ${run.level.breaths}"
                    WhPhase.Hold -> "Resta fermo e rilassato. Tocca appena senti il bisogno di respirare."
                    WhPhase.RecoveryInhale -> "Un respiro profondo."
                    WhPhase.RecoveryHold -> "Trattieni il respiro, senza forzare."
                    WhPhase.Release -> "Lascia andare l'aria."
                    WhPhase.Done -> ""
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (run.phase == WhPhase.Hold) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tempo massimo: ${run.level.maxHoldSec} secondi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (run.phase == WhPhase.Hold) {
            Button(onClick = onEndHold, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Text("Ho bisogno di respirare", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(onClick = onStop, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Fermati e respira normalmente")
        }
        Text(
            "Capogiri, vista offuscata, dolore al petto o sensazione strana: fermati subito.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Finished(run: WimHofRun, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Ben fatto.", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(12.dp))
        Text(
            "Le tue apnee: " + run.holdsSec.joinToString(" · ") { "$it s" } + ". Non è una gara: quello che conta è come stai.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Resta seduto o sdraiato un paio di minuti respirando normalmente, e alzati piano. Se hai ancora la testa leggera, aspetta ancora un po' prima di muoverti.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose, shape = CircleShape, modifier = Modifier.height(48.dp)) { Text("Chiudi") }
    }
}

@Composable
private fun Info(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
