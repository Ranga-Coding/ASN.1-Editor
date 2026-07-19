package com.asn1editor.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Haupteinstiegspunkt der ASN.1-Editor-Anwendung.
 *
 * <p>Lädt das FXML-Layout und zeigt die Haupt-UI an.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        VBox root = loader.load();

        primaryStage.setTitle("ASN.1 Editor");
        primaryStage.setScene(new Scene(root, 1000, 700));

        // Fenster-Icon laden
        InputStream iconStream = getClass().getResourceAsStream("/icon.png");
        if (iconStream != null) {
            primaryStage.getIcons().add(new Image(iconStream));
        }

        primaryStage.show();

        File startupFile = resolveStartupFile();
        if (startupFile != null) {
            MainController controller = loader.getController();
            controller.loadFile(startupFile);
        }
    }

    private File resolveStartupFile() {
        List<String> parameters = getParameters().getRaw();
        if (!parameters.isEmpty()) {
            Path argumentPath = Path.of(parameters.get(0));
            if (Files.exists(argumentPath)) {
                return argumentPath.toFile();
            }
        }

        Path defaultPath = Path.of("Test.crmf");
        if (Files.exists(defaultPath)) {
            return defaultPath.toFile();
        }
        return null;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
