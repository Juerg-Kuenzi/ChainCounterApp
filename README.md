# Ismé

Android-App für die Segelyacht Ismé: für den [ChainCounter](https://github.com/Juerg-Kuenzi/ChainCounter), den Kettenzähler einer Ankerwinde, und für Werte vom NMEA2000-Bus des Boots. Die App zeigt die Daten des Geräts (`$PXISWND`, bis Firmware 1.2.1 `$ISLWND`) live an und sendet Kommandos per UDP. Grundlage ist die [HW/SW-Spezifikation V1.7](https://github.com/Juerg-Kuenzi/ChainCounter/blob/main/docs/ChainCounterSpecification_V1.7.odt) im Firmware-Repository.

## Funktionen

- **Zwei Seiten**, gewechselt durch seitliches Wischen oder Tippen auf die Punkte oben rechts. Jede Seite warnt nur über ihre eigene Datenquelle.
- **Seite Ankerkette:** Kettenlänge, Richtung (Auf oder Ab) und Speisung. Kommt 3 s lang kein gültiger Satz, erscheint unter den Daten eine Warnung und die Länge wird abgeblendet.
- **Seite NMEA2000:** Abgastemperatur und Alarmwert. Kommt 6 s lang kein Wert, erscheint eine Warnung.
- **Abgasalarm:** Liegt die Temperatur über dem Alarmwert (Standard 50 °C), erscheint ein Alarmbalken auf beiden Seiten und alle 2 s ertönt ein Ton auf dem Alarm-Lautstärkekanal. „Ton aus“ schaltet den Ton bis zum nächsten Alarm stumm. Der Alarm endet 1 °C unter dem Alarmwert.
- **Menü > Einstellungen:** Hell/Dunkel, Zähler nullen (`C`), Länge setzen (`C,<m>`), Port ChainCounter, Entprellzeit (`S,<ms>`), Umrechnungsfaktor (`K,<m/U>`), Restart (`R`, ab Firmware 1.2.0), Port NMEA2000, Alarmwert Abgas
- **Menü > Informationen:** Systemstarts, Laufzeiten, Adressen von ChainCounter und Bridge, App-Version
- **Rückmeldung:** Jede Einstellung zeigt den zuletzt gesendeten Wert. Fällt der Empfang aus und kommt zurück, meldet die Seite „Empfang wieder hergestellt“.
- **Dunkles Design:** Rote Anzeige auf Schwarz, damit die Nachtsicht erhalten bleibt. Solange der Schalter nie betätigt wurde, folgt die App der Android-Einstellung.

Factory Reset (`F`) ist bewusst nicht enthalten. Die App läuft nur im Vordergrund und hält dabei den Bildschirm an.

## Verhalten am Gerät

- Das Gerät quittiert keine Kommandos. Die gemeldete Länge steht auf den Seiten „Zähler nullen“ und „Länge setzen“. Entprellzeit und Faktor meldet das Gerät nicht zurück, sie lassen sich nur an der seriellen Konsole mit `status` prüfen.
- Kommandos gehen per Unicast an die Adresse, von der die Datensätze kommen, und auf den eingestellten Port (Standard 1458).
- Die App bindet ihren Socket an das WLAN, auch wenn dieses keinen Internetzugang hat.
- Das Feld `ChainDirection` meldet den Zustand des Signals und nicht, ob die Kette läuft. Es bleibt also auch beim Stillstand gesetzt.

## NMEA2000

- **Bridge:** Yacht Devices YDWG-02, sendet im RAW-Format per UDP auf Port 1457 (einstellbar). Eine Zeile pro CAN-Frame, z. B. `01:36:48.770 R 15FD0C74 FF 00 0E 7A BD 04 FF FF`.
- **Abgastemperatur:** PGN 130316 „Temperature, Extended Range“ mit Quelle 14 (Exhaust Gas) und Instanz 0, beim Yacht-Devices-Sensor mit `YD:TYPE 14` eingestellt. PGN 130312 dient nur als Ersatz, solange keine 130316 kommt, weil sie nur bis etwa 382 °C reicht. Der Sensor wird an Quelle und Instanz erkannt, nicht an der Busadresse.
- **Doppelte Pakete:** Identische Pakete innerhalb von 50 ms werden verworfen.
- **Ton:** Nur solange die App im Vordergrund läuft. Die Lautstärke ist die Alarm-Lautstärke des Telefons.

## Bauen

Mit Android Studio öffnen, oder mit JDK 17 und installiertem Android SDK:

```
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/`. Der Workflow [`android.yml`](.github/workflows/android.yml) macht bei jedem Push dasselbe und stellt die APK als Artefakt bereit.

## Signaturschlüssel

Android installiert ein Update nur, wenn die neue APK mit demselben Schlüssel signiert ist wie die installierte App. Der Workflow signiert deshalb mit einem festen Schlüssel aus zwei Repository-Secrets (*Settings > Secrets and variables > Actions*):

| Secret | Inhalt |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Schlüsseldatei `chaincounter-release.p12` als Base64 in einer Zeile |
| `SIGNING_PASSWORD` | Passwort der Schlüsseldatei, Alias `chaincounter` |

- **Push auf `main`:** Ohne die Secrets schlägt der Lauf fehl. Zusätzlich prüft er, dass die APK das Zertifikat mit dem SHA-256-Fingerabdruck `5267a4774b658ff05b583b8e7bec96862c12af42b2410a04e77498f9ada0ef32` trägt.
- **Pull Requests ohne Secrets** (z. B. aus Forks): Der Lauf signiert mit einem Wegwerf-Schlüssel. Eine solche APK ersetzt die installierte App nicht.
- **Lokal:** Ohne weitere Einstellung gilt der Debug-Schlüssel des eigenen Rechners. Damit Builds aus Android Studio die CI-APK ersetzen können, im Projektordner eine Datei `keystore.properties` anlegen. Sie steht in `.gitignore`:

  ```
  storeFile=/pfad/zu/chaincounter-release.p12
  password=<Passwort>
  ```

Den Schlüssel sicher aufbewahren. Geht er verloren, lassen sich installierte Apps nur noch durch Deinstallieren und Neuinstallieren ersetzen.

Die App-Kennung ist `ch.sy_isme.app`, abgeleitet von der Domain sy-isme.ch. Bis Version 1.2.0 hiess die App „ChainCounter“ mit der Kennung `com.example.chaincounter`. Für Android ist das eine andere App: Die alte Version einmal deinstallieren.

## App-Icon

Das Icon ist die Silhouette aus dem Logo der Ismé, ohne Schriftzug. Die Kanten wurden im Scan vermessen und als glatte Kurven neu konstruiert, die Masten leicht verbreitert, damit sie auch klein sichtbar bleiben. Es ist ein adaptives Vektor-Icon (`drawable/ic_launcher_foreground.xml`) mit weissem Hintergrund und einer einfarbigen Variante für Themensymbole ab Android 13.

## Ohne Hardware testen

`tools/simulate_device.py` verhält sich im Netz wie das Gerät, `tools/simulate_ydwg.py` wie die Bridge mit dem Abgassensor. Die Temperatur steigt und fällt dabei, damit sich der Alarm testen lässt. PC und Telefon müssen im selben WLAN sein:

```
python3 tools/simulate_device.py --port 1458
python3 tools/simulate_ydwg.py --start 40 --step 2 --max 60
```

## Aufbau

```
app/src/main/java/ch/sy_isme/app/
  MainActivity.kt      Oberfläche und WLAN-Anbindung
  SessionState.kt      Datenstand, Datenausfall und Empfangsmeldung
  ExhaustMonitor.kt    Abgastemperatur, Datenausfall und Alarm
  net/UdpLink.kt       UDP senden und empfangen, ohne Android-Abhängigkeit
  protocol/Nmea.kt     Checksumme und Parser für $PXISWND (und $ISLWND)
  protocol/Commands.kt Kommandos ($ISLWNS) und Prüfung der Eingaben
  protocol/Format.kt   Zahlen und Laufzeiten formatieren
  protocol/YdRaw.kt    YDWG-02-RAW-Format, PGN aus der CAN-ID
  protocol/N2kTemperature.kt  PGN 130312/130316, Auswahl des Sensors
app/src/test/          JUnit-Tests für Protokoll, Datenausfall und UDP (Loopback)
```
