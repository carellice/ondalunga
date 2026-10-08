# Onda Lunga

Un party game per Android da giocare in compagnia con un solo telefono: siete sulla stessa lunghezza d'onda?

A ogni turno un giocatore, il **Sensitivo**, fa girare la ruota a schermo chiuso, poi sbircia dove si è fermato il bersaglio su uno spettro tra due estremi (per esempio *Freddo – Caldo*). Dà un solo indizio, richiude lo schermo, e la sua squadra deve trascinare la lancetta dove pensa che sia il bersaglio.

## Cosa c'è

- **A squadre**: 4, 3 o 2 punti in base alla fascia colpita, scommessa sinistra/destra degli avversari, turno extra in rimonta.
- **Cooperativa**: tutti insieme, un numero fisso di carte per fare più punti possibile.
- Ruota da far girare con il dito, lancetta da trascinare, schermo che scorre.
- 80 carte in italiano.
- Suoni, vibrazione e regole regolabili dalle impostazioni.

## Installazione

Scarica l'APK dall'ultima [release](../../releases/latest) e aprilo sul telefono. Serve Android 8.0 o successivo.

## Compilare dal sorgente

Servono Android Studio (o un JDK 17+ e l'SDK Android 36).

```bash
./gradlew installDebug
```

I suoni sono sintetizzati da `tools/make_sounds.py`; per rigenerarli:

```bash
python3 tools/make_sounds.py
```

`release.command` (macOS, doppio click) incrementa la versione, compila l'APK firmato e lo pubblica nelle release di GitHub. Richiede la [GitHub CLI](https://cli.github.com) e, alla prima esecuzione, crea una chiave di firma locale che non viene mai caricata nel repository.

## Struttura

| Percorso | Contenuto |
| --- | --- |
| `app/src/main/java/.../game` | Regole e carte, senza dipendenze da Android |
| `app/src/main/java/.../ui` | Schermate e componenti in Jetpack Compose |
| `app/src/test` | Test delle regole |

## Nota

Progetto amatoriale e indipendente, ispirato alla meccanica dei giochi "indovina il punto sullo spettro". Nome, grafica, suoni e carte sono originali; non è affiliato né approvato da alcun editore di giochi da tavolo.

## Licenza

[MIT](LICENSE)
