@echo off
echo ========================================
echo ASN.1 Editor Debug — Startet alles
echo ========================================
echo.

cd /d "c:\Workspace\ASN.1-Editor"

REM 1. Classpath erstellen
echo [1/4] Classpath erstellen...
call "c:\Tools\apache-maven-3.9.9\bin\mvn.cmd" dependency:build-classpath -Dmdep.outputFile=target\cp.txt -DincludeScope=runtime -q

REM 2. Java mit Debug-Agent starten
echo [2/4] Debug-JVM starten (Port 5005)...
start "" /b "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" ^
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 ^
  --add-modules javafx.controls,javafx.fxml ^
  --module-path "C:\Users\Ralf\.m2\repository\org\openjfx\javafx-controls\21\javafx-controls-21-win.jar;C:\Users\Ralf\.m2\repository\org\openjfx\javafx-fxml\21\javafx-fxml-21-win.jar;C:\Users\Ralf\.m2\repository\org\openjfx\javafx-graphics\21\javafx-graphics-21-win.jar;C:\Users\Ralf\.m2\repository\org\openjfx\javafx-base\21\javafx-base-21-win.jar" ^
  -cp target\classes;@target\cp.txt ^
  com.asn1editor.ui.Main Test.crmf

REM 3. Warten bis Port 5005 offen ist
echo [3/4] Warte auf Debug-Port 5005...
set /a retries=0
:waitloop
set /a retries+=1
netstat -an 2>nul | find "5005" | find "LISTENING" >nul
if errorlevel 1 (
    if %retries% gtr 30 (
        echo ERROR: Port 5005 nicht erreicht nach 30 Sekunden.
        pause
        exit /b 1
    )
    timeout /t 1 /nobreak >nul
    goto waitloop
)
echo [4/4] Port 5005 offen — VS Code wird gestartet...

REM 4. VS Code mit Debug-Session starten
code --extensionDevelopmentPath="${workspaceFolder}" "${workspaceFolder}"

echo.
echo Debugging gestartet!
echo.
echo Wenn die GUI nicht öffnet:
echo - In VS Code: F5 → "ASN.1 Editor (Attach Debug)"
echo - ODER: Debugger-Status prüfen
pause
