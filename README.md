# ChainCounterApp

Android-App für den [ChainCounter](https://github.com/Juerg-Kuenzi/ChainCounter), den Kettenzähler einer Ankerwinde. Die App zeigt die `$ISLWND`-Daten des Geräts live an und sendet `$ISLWNS`-Kommandos per UDP. Grundlage ist die [HW/SW-Spezifikation V1.5](https://github.com/Juerg-Kuenzi/ChainCounter/blob/main/docs/ChainCounterSpecification_V1.5.odt) im Firmware-Repository.

## Funktionen

- **Anzeige:** Kettenlänge, Richtung, Speisung, Systemstarts und beide Laufzeiten
- **Kommandos:** Zähler nullen (`C`), Länge setzen (`C,<m>`), Entprellzeit (`S,<ms>`), Umrechnungsfaktor (`K,<m/U>`)
- **Warnung bei Datenausfall:** Kommt 3 s lang kein gültiger Satz, erscheint ein Warnbalken, die Länge wird abgeblendet und die Kommandos sind gesperrt
- **Nachtmodus:** Rote Anzeige auf Schwarz, wenn das Telefon im dunklen Design läuft

Factory Reset (`F`) ist bewusst nicht enthalten. Die App läuft nur im Vordergrund und hält dabei den Bildschirm an.

## Verhalten am Gerät

- Das Gerät quittiert keine Kommandos. Nach `C` zeigt die App, welche Länge das Gerät 2,5 s später meldet. Entprellzeit und Faktor meldet das Gerät nicht zurück, sie lassen sich nur an der seriellen Konsole mit `status` prüfen.
- Kommandos gehen per Unicast an die Adresse, von der die `$ISLWND`-Sätze kommen, und auf den eingestellten Port (Standard 1467).
- Die App bindet ihren Socket an das WLAN, auch wenn dieses keinen Internetzugang hat.
- Das Feld `ChainDirection` meldet den Zustand des Signals und nicht, ob die Kette läuft. Es bleibt also auch beim Stillstand gesetzt.

## Bauen

Mit Android Studio öffnen, oder mit JDK 17 und installiertem Android SDK:

```
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/`. Der Workflow [`android.yml`](.github/workflows/android.yml) macht bei jedem Push dasselbe und stellt die APK als Artefakt bereit. Jeder Lauf signiert mit einem neuen Debug-Schlüssel, deshalb muss die App vor der Installation einer neuen CI-APK deinstalliert werden.

Die `applicationId` `com.example.chaincounter` ist ein Platzhalter und vor einer Veröffentlichung im Play Store zu ersetzen.

## Ohne Hardware testen

`tools/simulate_device.py` verhält sich im Netz wie das Gerät. PC und Telefon müssen im selben WLAN sein:

```
python3 tools/simulate_device.py --port 1467
```

## Aufbau

```
app/src/main/java/com/example/chaincounter/
  MainActivity.kt      Oberfläche, WLAN-Anbindung, Datenausfall-Erkennung
  net/UdpLink.kt       UDP senden und empfangen, ohne Android-Abhängigkeit
  protocol/Nmea.kt     Checksumme und Parser für $ISLWND
  protocol/Commands.kt $ISLWNS-Kommandos und Prüfung der Eingaben
  protocol/Format.kt   Zahlen und Laufzeiten formatieren
app/src/test/          JUnit-Tests für Protokoll und UDP (Loopback)
```
