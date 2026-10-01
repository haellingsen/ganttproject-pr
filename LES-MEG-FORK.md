# GanttProject – PR fork

Dette er **hovedmappa for utvikling** av vår GanttProject-variant.
Oppstrøms er `bardsoftware/ganttproject`; våre endringer ligger på egen branch.

| | |
|---|---|
| Versjon | 3.4.3395 |
| Oppstrøms | `origin` = https://github.com/bardsoftware/ganttproject.git |
| Installert app | `%USERPROFILE%\Apps\ganttproject-3.4.3395-fx` |
| Portabel pakke | `%USERPROFILE%\Apps\GanttProject-PR-portable-3.4.3395.zip` |

## Våre tillegg

- Dokkbart **avhengighetspanel** i Gantt-fanen (Vis → Task dependencies). Koblingstype (FS, SS, FF, SF), forsinkelse eller forsering i dager, og hard/rubber endres rett i tabellen
- Innstillingssider migrert til JavaFX
- Disposisjonsnivåer (Alt+1..9), sentrert zoom, klikkbart PERT-diagram
- Rettelser: ny/slett oppgave, og angre etter Ctrl+dra i Gantt-diagrammet
- **Oversiktsdiagram (PDF/SVG)**: enkel rapport på én side, standardvalget øverst i Prosjekt → Eksporter. Se [brukerveiledningen](docs/oversiktseksport.md)
- Eksport legger til filendelsen hvis du ikke skriver den

## Bygge

Krever **Liberica JDK 21 Full** – vanlig JDK gir `package javafx does not exist`.

```powershell
$env:JAVA_HOME = "C:\Program Files\BellSoft\LibericaJDK-21-Full"
.\gradlew.bat distbin
```

Resultatet havner i `ganttproject-builder\dist-bin`.
Kjør derfra med `ganttproject.bat` eller `ganttproject.exe`.

## Oppdatere den installerte appen

Kopier ny jar til installasjonen (ta vare på den gamle i `_original-jars\`):

```powershell
$app = "$env:USERPROFILE\Apps\ganttproject-3.4.3395-fx"
Copy-Item ganttproject-builder\dist-bin\plugins\base\ganttproject\lib\ganttproject-*.jar.lib `
          "$app\plugins\base\ganttproject\lib\" -Force
```

Appen startes med `start-ganttproject-fx.bat` i samme mappe.

## Lage portabel pakke

```powershell
$env:JAVA_HOME = "C:\Program Files\BellSoft\LibericaJDK-21-Full"
.\gradlew.bat clean distBin
.\ganttproject-builder\make-portable.ps1
```

Skriptet pakker `ganttproject-builder\dist-bin` sammen med jlink-kjøremiljøet
(OpenJDK 21 + JavaFX) i `%USERPROFILE%\Apps\ganttproject-portable-build\runtime`
til `out\GanttProject-PR` i samme mappe. Zip den mappa etterpå.
Pakken krever ingen Java-installasjon hos mottaker.

For én enkelt exe-fil med alt i (Java, programmet og pluginene):

```powershell
.\ganttproject-builder\single-exe\make-single-exe.ps1 -OutFile "$env:USERPROFILE\Apps\GanttProject-PR.exe"
```

Første gang exe-filen startes, pakker den ut programmet til `%LOCALAPPDATA%\GanttProject-PR\<bygg>`
(ca. 10 sekunder). Senere starter den den utpakkede kopien direkte. En nyere exe pakker ut sin egen kopi
og sletter de gamle. Skriptet bruker C#-kompilatoren som følger med Windows.

Pakken må starte med `-Dlogback.configurationFile=$APPDIR\logback.xml`, og skriptet
setter den. Uten den finner ikke logback konfigurasjonen, fordi jpackage bare legger
jar-filene på classpath. Da logger alt på DEBUG til en konsoll som ikke finnes, og
kopier/lim inn blir svært tregt.

## Merk

- Submodulen `biz.ganttproject.app.localization` må være sjekket ut:
  `git submodule update --init`
- Generert CSS, jar-filer i `ganttproject-builder\lib` og `*.log` er
  byggeartefakter og ignoreres av git.
