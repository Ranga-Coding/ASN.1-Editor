@echo off
echo ========================================
echo ASN.1 Editor — Debug-Start
echo ========================================
echo.
echo 1. Dieses Fenster OFFEN LASSEN!
echo 2. In VS Code: F5 drucken
echo 3. Debugger verbindet sich automatisch
echo.
echo ========================================
echo.

cd /d "c:\Workspace\ASN.1-Editor"

REM Classpath erstellen
call "c:\Tools\apache-maven-3.9.9\bin\mvn.cmd" dependency:build-classpath -Dmdep.outputFile=target\cp.txt -DincludeScope=runtime -q

REM Java mit Debug-Agent starten
"C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe" ^
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 ^
  --add-modules javafx.controls,javafx.fxml ^
  --module-path "C:\Users\Ralf\.m2\repository\org\openjfx\javafx-controls\21\javafx-controls-21-win.jar;C:\Users\Ralf\.m2\repository\org\openjfx\javafx-fxml\21\javafx-fxml-21-win.jar;C:\Users\Ralf\.m2\repository\org\openjfx\javafx-graphics\21\javafx-graphics-21-win.jar;C:\Users\Ralf\.m2\repository\org\openjfx\javafx-base\21\javafx-base-21-win.jar" ^
  -cp target\classes;@target\cp.txt ^
  com.asn1editor.ui.Main Test.crmf

pause
