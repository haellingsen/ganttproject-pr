# Lager en portabel GanttProject-PR-pakke (jpackage app-image) fra dist-bin.
# Kjoer ".\gradlew.bat clean distBin" med Liberica JDK 21 Full foerst.
# Arbeidsmappa maa inneholde et jlink-kjoeremiljoe i "runtime" (OpenJDK 21 + JavaFX).
param(
  [string]$WorkDir = "$env:USERPROFILE\Apps\ganttproject-portable-build"
)
$ErrorActionPreference = "Stop"
$src = Join-Path $PSScriptRoot "dist-bin"
$scratch = $WorkDir
$stage = Join-Path $scratch "input"
$outdir = Join-Path $scratch "out"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (Test-Path $stage) { Rename-Item $stage "input-old-$stamp" }
if (Test-Path $outdir) { Rename-Item $outdir "out-old-$stamp" }
New-Item -ItemType Directory -Force $stage, $outdir | Out-Null

Copy-Item "$src\eclipsito.jar", "$src\LICENSE", "$src\logback.xml", "$src\logging.properties", "$src\HouseBuildingSample.gan" $stage
Copy-Item "$src\lib" "$stage\lib" -Recurse
Copy-Item "$src\plugins" "$stage\plugins" -Recurse
"Main jar(s) in stage:"
Get-ChildItem "$stage\plugins\base\ganttproject\lib\ganttproject-*.jar.lib" | ForEach-Object { "  " + $_.Name }

$opts = @(
 "-Duser.dir=`$APPDIR", "-DversionDirs=plugins", "-Dapp=net.sourceforge.ganttproject.GanttProject",
 "-Dlogback.configurationFile=`$APPDIR\logback.xml", "-Dgpcloud=prod", "-Dorg.jooq.no-logo=true", "-Xms32m", "-Xmx2048m", "-Dsun.java2d.d3d=false", "-ea",
 "--add-exports javafx.controls/com.sun.javafx.scene.control.behavior=ALL-UNNAMED",
 "--add-exports javafx.base/com.sun.javafx=ALL-UNNAMED",
 "--add-exports javafx.base/com.sun.javafx.event=ALL-UNNAMED",
 "--add-exports javafx.base/com.sun.javafx.logging=ALL-UNNAMED",
 "--add-exports javafx.controls/com.sun.javafx.scene.control=ALL-UNNAMED",
 "--add-exports javafx.controls/com.sun.javafx.scene.control.skin=ALL-UNNAMED",
 "--add-exports javafx.controls/com.sun.javafx.scene.control.skin.resources=ALL-UNNAMED",
 "--add-exports javafx.controls/com.sun.javafx.scene.control.inputmap=ALL-UNNAMED",
 "--add-exports javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
 "--add-exports javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
 "--add-exports javafx.graphics/com.sun.javafx.scene.traversal=ALL-UNNAMED",
 "--add-exports javafx.graphics/com.sun.javafx.scene=ALL-UNNAMED",
 "--add-exports javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED",
 "--add-exports javafx.graphics/com.sun.javafx.util=ALL-UNNAMED",
 "--add-opens java.desktop/sun.swing=ALL-UNNAMED")

$jargs = @("-t","app-image","-d",$outdir,"-i",$stage,"-n","GanttProject-PR",
  "--main-class","com.bardsoftware.eclipsito.Launch","--main-jar","eclipsito.jar",
  "--runtime-image",(Join-Path $scratch "runtime"),"--app-version","3.4.3395",
  "--icon",(Join-Path $PSScriptRoot "ganttproject.ico"),
  "--description","GanttProject 3.4 med avhengighetspanel (Pioneer Robotics fork)",
  "--vendor","Pioneer Robotics")
foreach ($o in $opts) { $jargs += "--java-options"; $jargs += $o }

& "C:\Program Files\BellSoft\LibericaJDK-21-Full\bin\jpackage.exe" @jargs 2>&1 | Select-Object -Last 5
"Result:"
Get-ChildItem (Join-Path $outdir "GanttProject-PR") | ForEach-Object { "  " + $_.Name }
"{0:N0} MB" -f ((Get-ChildItem (Join-Path $outdir "GanttProject-PR") -Recurse | Measure-Object Length -Sum).Sum / 1MB)
