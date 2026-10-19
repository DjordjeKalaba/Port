package ba.etfbl.luka;

import ba.etfbl.luka.service.AppLogger;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class LukaApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader =
                new FXMLLoader(LukaApplication.class.getResource("admin-view.fxml"));

        Scene scene = new Scene(loader.load(), 900, 600);

        stage.setTitle("Luka - Administratorski dio");
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                AppLogger.error("Neocekivana greska u niti " + thread.getName() + ".", throwable));
        launch(args);
    }
}
