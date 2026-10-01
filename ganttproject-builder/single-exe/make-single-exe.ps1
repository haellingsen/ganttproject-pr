# Lager en enkelt GanttProject-PR.exe som inneholder hele den portable pakken.
# Kjoer make-portable.ps1 foerst. Skriptet bruker C#-kompilatoren som foelger med Windows (.NET Framework 4).
#
# Exe-filen pakker ut programmet til %LOCALAPPDATA%\GanttProject-PR\<bygg> foerste gang den startes,
# og starter den utpakkede kopien senere.
param(
  [string]$WorkDir = "$env:USERPROFILE\Apps\ganttproject-portable-build",
  [string]$OutFile = "$env:USERPROFILE\Apps\GanttProject-PR.exe"
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$appImage = Join-Path $WorkDir "out\GanttProject-PR"
if (-not (Test-Path (Join-Path $appImage "GanttProject-PR.exe"))) { throw "Fant ikke $appImage. Kjoer make-portable.ps1 foerst." }

$tmp = Join-Path $WorkDir "single-exe"
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory $tmp | Out-Null

$zip = Join-Path $tmp "payload.zip"
[System.IO.Compression.ZipFile]::CreateFromDirectory($appImage, $zip, [System.IO.Compression.CompressionLevel]::Optimal, $true)
$build = "3.4.3395-" + (Get-Date -Format "yyyyMMdd-HHmmss")
Set-Content -Path (Join-Path $tmp "build.txt") -Value $build -NoNewline -Encoding ascii

$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
& $csc /nologo /target:winexe /optimize+ "/out:$OutFile" `
  "/win32icon:$(Join-Path $PSScriptRoot '..\ganttproject.ico')" `
  "/resource:$zip,payload.zip" "/resource:$(Join-Path $tmp 'build.txt'),build.txt" `
  /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll `
  (Join-Path $PSScriptRoot "Launcher.cs")
if ($LASTEXITCODE -ne 0) { throw "csc feilet" }
Remove-Item $tmp -Recurse -Force
"Bygg: $build"
"{0}  {1:N0} MB" -f $OutFile, ((Get-Item $OutFile).Length / 1MB)
