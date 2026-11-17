# Stato verificato della build — HerobrineLegend 0.4.2

- Corretto `api-version` da `26.3` (non valido per plugin.yml) a `1.21`.
- Cambiato il riferimento Spigot API da `26.3-R0.1-SNAPSHOT` a `1.21.8-R0.1-SNAPSHOT`.
- Impostata la toolchain a Java 21, per compilare contro Spigot 1.21.8.
- Esteso `config.yml` con le sezioni e i valori di default di Herobrine 1.8,
  preservando le chiavi che il codice di sviluppo già legge.
- Il config originale era stato fornito in chat: l'estensione include i valori
  funzionali principali ma non riproduce parola per parola ogni commento originale.
- NOTA: varie chiavi della configurazione 1.8 sono al momento *solo configurazione*;
  la logica Java non le legge ancora. Non considerare completa la compatibilità.
- NON compilato: l'ambiente corrente ha Java 21 ma non Maven e non può raggiungere
  i repository Maven (errore DNS). Il workflow GitHub Actions resta disponibile.
- NON testato su Spigot/Paper. Versione server dell'utente ancora da confermare.
- Il codice NPC PacketEvents va ancora validato contro l'API 2.14.0.
