@echo off
REM ASN.1 Editor — Run & Debug Script
REM Kompiliert und startet die Anwendung mit korrektem --module-path für JavaFX.

set JAVA_EXE=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe
set MVN_EXE=c:\Tools\apache-maven-3.9.9\bin\mvn.cmd

REM Maven-Cache-Pfad
set MAVEN_REPO=%USERPROFILE%\.m2\repository

REM JavaFX JARs im Modulpfad (platform-unabhängig für Module-System)
set MODULE_PATH=%MAVEN_REPO%\org\openjfx\javafx-controls\21\javafx-controls-21.jar
set MODULE_PATH=%MODULE_PATH%;%MAVEN_REPO%\org\openjfx\javafx-fxml\21\javafx-fxml-21.jar
set MODULE_PATH=%MODULE_PATH%;%MAVEN_REPO%\org\openjfx\javafx-graphics\21\javafx-graphics-21.jar
set MODULE_PATH=%MODULE_PATH%;%MAVEN_REPO%\org\openjfx\javafx-base\21\javafx-base-21.jar
set MODULE_PATH=%MODULE_PATH%;%MAVEN_REPO%\org\openjfx\javafx-swt\21\javafx-swt-21.jar

REM Maven Dependencies (class path für das eigene Projekt)
set CLASS_PATH=%MAVEN_REPO%\org\junit\jupiter\junit-jupiter-api\5.10.0\junit-jupiter-api-5.10.0.jar
set CLASS_PATH=%CLASS_PATH%;%MAVEN_REPO%\org\junit\jupiter\junit-jupiter-engine\5.10.0\junit-jupiter-engine-5.10.0.jar
set CLASS_PATH=%CLASS_PATH%;%MAVEN_REPO%\org\opentest4j\opentest4j\1.3.0\opentest4j-1.3.0.jar
set CLASS_PATH=%CLASS_PATH%;%MAVEN_REPO%\org\junit\platform\junit-platform-commons\1.10.0\junit-platform-commons-1.10.0.jar
set CLASS_PATH=%CLASS_PATH%;%MAVEN_REPO%\org\apiguardian\apiguardian-api\1.1.2\apiguardian-api-1.1.2.jar
set CLASS_PATH=%CLASS_PATH%;%MAVEN_REPO%\org\openjfx\javafx-controls\21\javafx-controls-21-win.jar
set CLASS_PATH=%CLASS_PATH%;%MAVEN_REPO%\org\openjfx\javafx-fxml\21\javafx-fxml-21-win.jar
set CLASS_PATH=%CLASS_PATH%;%MAVEN_REPO%\org\openjfx\javafx-graphics\21\javafx-graphics-21-win.jar
set CLASS_PATH=%CLASS_PATH%;%MAVEN_REPO%\org\openjfx\javafx-base\21\javafx-base-21-win.jar

REM Kompilieren
echo [1/2] Kompiliere Projekt...
%MVN_EXE% clean compile -q
if errorlevel 1 (
    echo FEHLER: Kompilierung fehlgeschlagen.
    pause
    exit /b 1
)

REM Starten (mit --add-modules für JavaFX, classpath für Projekt-Code)
echo [2/2] Starte ASN.1 Editor...
%JAVA_EXE% ^
    --module-path %MODULE_PATH% ^
    --add-modules javafx.controls,javafx.fxml ^
    -cp target\classes ^
    com.asn1editor.ui.Main Test.crmf

pause
