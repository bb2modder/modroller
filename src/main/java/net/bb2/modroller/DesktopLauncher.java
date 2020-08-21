package net.bb2.modroller;

import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import net.bb2.modroller.scenes.ExampleScene;
import net.bb2.modroller.scenes.InitialSetupScene;

import java.io.File;
import java.util.Optional;

public class DesktopLauncher extends Application {

	public static void main(String[] args) {
	    launch(args);
	}

    @Override
    public void start(Stage primaryStage) throws Exception {
	    primaryStage.getIcons().add(new Image("file:assets/deathroller.gif"));
        primaryStage.setTitle("Modroller");


	    InitialSetupScene initialSetupScene = new InitialSetupScene(primaryStage);
	    primaryStage.setScene(initialSetupScene.getScene());

	    primaryStage.show();
    }
}
