package net.bb2.modroller;

import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import net.bb2.modroller.scenes.FindBaseDirScene;
import net.bb2.modroller.scenes.ProcessPackagesScene;

public class DesktopLauncher extends Application {

	public static void main(String[] args) {
	    launch(args);
	}

    @Override
    public void start(Stage primaryStage) throws Exception {
	    primaryStage.getIcons().add(new Image("file:assets/deathroller.gif"));
        primaryStage.setTitle("Modroller");

	    Injector injector = Guice.createInjector();

	    FindBaseDirScene findBaseDirScene = injector.getInstance(FindBaseDirScene.class);
	    findBaseDirScene.initialise(primaryStage);

	    ProcessPackagesScene processPackagesScene = injector.getInstance(ProcessPackagesScene.class);


	    findBaseDirScene.setOnComplete(() -> {
		    processPackagesScene.show(primaryStage);
	    });

	    findBaseDirScene.show(primaryStage);

	    primaryStage.show();
    }
}
