# GanttProject – Pioneer Robotics-fork

Dette er **hovedmappa for utvikling** av vår GanttProject-variant.
Oppstrøms er `bardsoftware/ganttproject`; våre endringer ligger på egen branch.

| | |
|---|---|
| Arbeidsbranch | `feature/fx-option-pages` |
| Versjon | 3.4.3395 |
| Oppstrøms | `origin` = https://github.com/bardsoftware/ganttproject.git |
| Installert app | `%USERPROFILE%\Apps\ganttproject-3.4.3395-fx` |
| Portabel pakke | `%USERPROFILE%\Apps\GanttProject-PR-portable-3.4.3395.zip` |

## Våre tillegg

- Dokkbart **avhengighetspanel** i Gantt-fanen (Vis → Task dependencies)
- Innstillingssider migrert til JavaFX
- Disposisjonsnivåer (Alt+1..9), sentrert zoom, klikkbart PERT-diagram
- Rettelser: ny/slett oppgave, og angre etter Ctrl+dra i Gantt-diagrammet

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
& "$env:USERPROFILE\Apps\ganttproject-portable-build\make-portable.ps1"
```

Skriptet pakker den installerte appen sammen med et jlink-kjøremiljø
(OpenJDK 21 + JavaFX) til `out\GanttProject-PR`. Zip den mappa etterpå.
Pakken krever ingen Java-installasjon hos mottaker.

## Merk

- Submodulen `biz.ganttproject.app.localization` må være sjekket ut:
  `git submodule update --init`
- Generert CSS, jar-filer i `ganttproject-builder\lib` og `*.log` er
  byggeartefakter og ignoreres av git.
