; Build the complete Windows release folder before compiling this script.
#define ReleaseDir "..\..\ScorekeeperAppRelease"
#define IconFile "..\..\frontend\src\main\resources\app_icon.ico"

#ifnexist ReleaseDir + "\ScoreKeeper.exe"
  #error "Launcher not found in ScorekeeperAppRelease. Build the release first."
#endif
#ifnexist ReleaseDir + "\BackendRelease\ScoreKeeperAppBackend.exe"
  #error "Backend not found in ScorekeeperAppRelease\BackendRelease."
#endif
#ifnexist ReleaseDir + "\FrontendRelease\ScoreKeeper\ScoreKeeper.exe"
  #error "Frontend not found in ScorekeeperAppRelease\FrontendRelease\ScoreKeeper."
#endif

[Setup]
AppId={{89D3C442-EFB2-4843-85D1-B0CF50245A2A}
AppName=ScoreKeeper
AppVersion=1.0.0
DefaultDirName={localappdata}\Programs\ScoreKeeper
DefaultGroupName=ScoreKeeper
PrivilegesRequired=lowest
OutputDir=..\..\installer-output
OutputBaseFilename=ScoreKeeper-Setup
SetupIconFile={#IconFile}
UninstallDisplayIcon={app}\ScoreKeeper.exe
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
CloseApplications=yes

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "Создать ярлык на рабочем столе"; Flags: unchecked

[Files]
Source: "{#ReleaseDir}\ScoreKeeper.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#ReleaseDir}\BackendRelease\*"; DestDir: "{app}\BackendRelease"; Excludes: "server.log"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#ReleaseDir}\FrontendRelease\ScoreKeeper\*"; DestDir: "{app}\FrontendRelease\ScoreKeeper"; Excludes: "*.jar"; Flags: ignoreversion recursesubdirs createallsubdirs
; No skipifsourcedoesntexist: compilation must fail if application JARs are absent.
Source: "{#ReleaseDir}\FrontendRelease\ScoreKeeper\app\*.jar"; DestDir: "{app}\FrontendRelease\ScoreKeeper\app"; Flags: ignoreversion

[Icons]
Name: "{group}\ScoreKeeper"; Filename: "{app}\ScoreKeeper.exe"; WorkingDir: "{app}"
Name: "{autodesktop}\ScoreKeeper"; Filename: "{app}\ScoreKeeper.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\ScoreKeeper.exe"; Description: "Запустить ScoreKeeper"; Flags: nowait postinstall skipifsilent
