# ChainCounterApp

Android-App für den [ChainCounter](https://github.com/Juerg-Kuenzi/ChainCounter), den Kettenzähler einer Ankerwinde. Die App zeigt die `$ISLWND`-Daten des Geräts live an und sendet `$ISLWNS`-Kommandos per UDP. Grundlage ist die [HW/SW-Spezifikation V1.6](https://github.com/Juerg-Kuenzi/ChainCounter/blob/main/docs/ChainCounterSpecification_V1.6.odt) im Firmware-Repository.

## Funktionen

- **Hauptbildschirm:** Kettenlänge, Richtung (Auf oder Ab) und Speisung. Kommt 3 s lang kein gültiger Satz, erscheint unter den Daten eine Warnung und die Länge wird abgeblendet.
- **Menü > Einstellungen:** Hell/Dunkel, Zähler nullen (`C`), Länge setzen (`C,<m>`), Portnummer, Entprellzeit (`S,<ms>`), Umrechnungsfaktor (`K,<m/U>`), Restart (`R`, ab Firmware 1.2.0)
- **Menü > Informationen:** Systemstarts, Laufzeiten, Adresse des Geräts, App-Version
- **Rückmeldung:** Jede Einstellung zeigt den zuletzt gesendeten Wert. Fällt der Empfang aus und kommt zurück, meldet die Seite „Empfang wieder hergestellt“.
- **Dunkles Design:** Rote Anzeige auf Schwarz, damit die Nachtsicht erhalten bleibt. Solange der Schalter nie betätigt wurde, folgt die App der Android-Einstellung.

Factory Reset (`F`) ist bewusst nicht enthalten. Die App läuft nur im Vordergrund und hält dabei den Bildschirm an.

## Verhalten am Gerät

- Das Gerät quittiert keine Kommandos. Die gemeldete Länge steht auf den Seiten „Zähler nullen“ und „Länge setzen“. Entprellzeit und Faktor meldet das Gerät nicht zurück, sie lassen sich nur an der seriellen Konsole mit `status` prüfen.
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
  MainActivity.kt      Oberfläche und WLAN-Anbindung
  SessionState.kt      Datenstand, Datenausfall und Empfangsmeldung
  net/UdpLink.kt       UDP senden und empfangen, ohne Android-Abhängigkeit
  protocol/Nmea.kt     Checksumme und Parser für $ISLWND
  protocol/Commands.kt $ISLWNS-Kommandos und Prüfung der Eingaben
  protocol/Format.kt   Zahlen und Laufzeiten formatieren
app/src/test/          JUnit-Tests für Protokoll, Datenausfall und UDP (Loopback)
```
