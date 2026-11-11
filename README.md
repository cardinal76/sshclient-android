# SSH Client

Client SSH per Android: si collega a un server con una chiave privata
custodita sul telefono e apre un terminale.

- **Chiavi**: se ne genera una nuova sul telefono (Ed25519) o se ne importa
  una esistente da file (OpenSSH, PEM, PuTTY; RSA, ECDSA, Ed25519, anche con
  passphrase). La chiave pubblica si copia negli appunti per metterla in
  `~/.ssh/authorized_keys` sul server.
- **Server**: indirizzo, porta, utente e chiave da usare.
- **Terminale**: xterm a 256 colori, con una barra per i tasti che la
  tastiera del telefono non ha (ESC, TAB, CTRL, ALT, frecce...).

L'autenticazione e' solo a chiave pubblica: niente password.

## Dove sta la chiave privata

In `files/keys/<id>.bin`, nella memoria privata dell'app, cifrata con
AES-256-GCM. La chiave AES sta nell'Android Keystore e non e' esportabile,
quindi il file copiato fuori dal telefono non si legge. Per lo stesso motivo
l'app e' esclusa dai backup: una copia ripristinata altrove sarebbe inutile.
Una chiave importata con passphrase viene salvata decifrata dentro questo
involucro, e la passphrase non si chiede piu'.

Disinstallare l'app cancella le chiavi. Di una chiave generata sul telefono
non esiste un'altra copia.

## La chiave del server

Alla prima connessione l'app mostra l'impronta della chiave del server
(`SHA256:...`, la stessa di `ssh-keygen -l`) e chiede conferma; da li' in poi
accetta solo quella. Se cambia, la connessione si ferma senza chiedere: se il
cambio e' voluto (server reinstallato) si usa «Dimentica la chiave del
server» nella scheda del server.

## Com'e' fatto

Kotlin e Jetpack Compose, un solo modulo `app`.

| Pacchetto | Cosa fa |
| --- | --- |
| `ssh` | Connessione ([sshj](https://github.com/hierynomus/sshj)), chiavi, server noti. Niente Android: si prova sulla JVM. |
| `data` | Archivio di chiavi e server, cifratura con l'Android Keystore. |
| `ui` | Le schermate. Il terminale e' [termlib](https://github.com/connectbot/termlib) di ConnectBot. |

## Compilare

Servono JDK 17 o superiore e l'SDK di Android (piattaforma 37.2).

```bash
./gradlew testDebugUnitTest assembleDebug
```

L'APK esce in `app/build/outputs/apk/debug/app-debug.apk`. I test accendono
un server SSH vero (Apache MINA) dentro la JVM e ci si collegano.

## Cosa manca

- La connessione vive finche' l'app resta in memoria: manca un servizio in
  primo piano che la tenga aperta quando Android chiude l'app sullo sfondo.
- Una sessione alla volta.
- Niente sblocco biometrico prima di usare una chiave.
- Niente inoltro di porte ne' SFTP.
