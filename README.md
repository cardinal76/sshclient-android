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

## La connessione resta aperta sullo sfondo

Finche' una connessione e' aperta l'app tiene acceso un servizio in primo
piano, con una notifica fissa («Connessione SSH aperta»). In cambio Android
non chiude l'app quando si passa a un'altra o si spegne lo schermo, e
tornando si ritrova il terminale com'era. «Disconnetti» nella notifica chiude
la connessione; il servizio si spegne da solo quando la sessione finisce.

Il servizio non riapre una connessione caduta (cambio di rete, server
riavviato): la sessione si chiude e ci si ricollega a mano.

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

## Firma

Android aggiorna un'app solo con un APK firmato dalla stessa chiave;
altrimenti la fa disinstallare, e disinstallare cancella le chiavi SSH. La
firma di debug predefinita e' diversa su ogni macchina, quindi l'APK di
GitHub Actions non aggiornerebbe quello compilato altrove. Per questo `debug`
e `release` si firmano con una chiave fissa, che sta in un keystore fuori dal
repository e arriva alla build da tre variabili d'ambiente:

| Variabile | Cosa contiene |
| --- | --- |
| `FIRMA_KEYSTORE` | percorso del keystore (PKCS12) |
| `FIRMA_PASSWORD` | la sua password |
| `FIRMA_ALIAS` | alias della chiave (`sshclient`) |

Su GitHub Actions servono due secret del repository (Settings -> Secrets and
variables -> Actions):

- `FIRMA_KEYSTORE_BASE64`: il keystore in base64 su una riga
  (`base64 -w0 sshclient-firma.p12`);
- `FIRMA_PASSWORD`: la password del keystore.

L'alias e' scritto nel workflow.

Senza keystore si compila lo stesso: Gradle avvisa e firma con la chiave di
debug della macchina (e' il caso di chi clona il repository e delle pull
request da un fork, che i secret non li vedono). Quell'APK pero' non aggiorna
un'app firmata con la chiave fissa: va installato su un telefono che non ce
l'ha, o dopo averla disinstallata.

Keystore e password non vanno mai nel repository. Chi perde il keystore non
puo' piu' pubblicare aggiornamenti: resta solo disinstallare.

## Cosa manca

- Una sessione alla volta.
- Niente sblocco biometrico prima di usare una chiave.
- Niente inoltro di porte ne' SFTP.
