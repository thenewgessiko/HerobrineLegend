# HerobrineLegend 0.3.1

Plugin sperimentale per **Spigot 26.3**, compilato con **Java 25** e dipendente da **PacketEvents 2.14.0**.

## Dipendenze richieste sul server

1. Spigot 26.3 (Java 25).
2. PacketEvents 2.14.0 o successivo compatibile con 26.3, installato separatamente nella cartella `plugins`.
3. `HerobrineLegend-0.3.1.jar` nella cartella `plugins`.

PacketEvents è una dipendenza `provided`: non viene incorporato nel JAR di HerobrineLegend.

## Compilazione locale

Servono JDK 25 e Maven 3.9+:

```bash
mvn clean package
```

Il risultato sarà `target/herobrinelegend-0.3.1.jar`.

## Compilazione automatica con GitHub Actions

Il workflow `.github/workflows/build.yml` compila il progetto con Java 25 a ogni push e su richiesta manuale. L'artefatto `HerobrineLegend-0.3.1` conterrà il JAR se la compilazione termina correttamente.

## Ritual

- Base 3x3: otto blocchi d'oro attorno al centro.
- Centro: `MOSSY_COBBLESTONE`.
- Sopra il centro: `NETHERRACK`.
- Redstone dust (`REDSTONE_WIRE`) sopra i quattro blocchi d'oro cardinali.
- Lancia `CRYING_OBSIDIAN` sulla netherrack per evocare.
- Lancia `DIAMOND` sulla stessa netherrack per disattivare immediatamente Herobrine.

Lo stato e la posizione del rituale sono salvati in `plugins/HerobrineLegend/state.yml` e persistono ai riavvii.

## Configurazione

Il file `config.yml` viene creato al primo avvio. Il danno è espresso in punti vita: `4.0` equivale a due cuori. Il tempo e il meteo del mondo non vengono modificati.

Comandi:

- `/herobrinelegend status`
- `/herobrinelegend reload`

Permesso: `herobrinelegend.admin` (default: op).

## Stato del progetto

Questa è una build sorgente di sviluppo. La compilazione CI è stata predisposta ma il JAR deve ancora superare una build reale e un test su un server Spigot 26.3 con PacketEvents. L'NPC è client-side (PacketEvents), quindi movimento, collisioni e navigazione sono simulati dal plugin e vanno verificati in gioco.
