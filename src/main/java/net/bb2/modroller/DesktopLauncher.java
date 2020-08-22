package net.bb2.modroller;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import net.bb2.modroller.scenes.FindBaseDirScene;
import net.bb2.modroller.scenes.GitUpdateScene;
import net.bb2.modroller.scenes.ModManagerScene;
import net.bb2.modroller.scenes.ProcessPackagesScene;

public class DesktopLauncher extends Application {

	public static void main(String[] args) {
	    launch(args);
	}

    @Override
    public void start(Stage primaryStage) throws Exception {
	    primaryStage.getIcons().add(new Image("file:assets/deathroller.gif"));
        primaryStage.setTitle("Modroller");


	    FindBaseDirScene findBaseDirScene =  new FindBaseDirScene();
	    findBaseDirScene.initialise(primaryStage);

	    ProcessPackagesScene processPackagesScene = new ProcessPackagesScene();
	    GitUpdateScene gitUpdateScene = new GitUpdateScene();
	    ModManagerScene modManagerScene = new ModManagerScene();

	    findBaseDirScene.setOnComplete(() -> {
		    processPackagesScene.show(primaryStage);
	    });
	    processPackagesScene.setOnComplete(() -> {
	    	gitUpdateScene.show(primaryStage);
	    });
	    gitUpdateScene.setOnComplete(() -> {
		    modManagerScene.show(primaryStage);
	    });

	    findBaseDirScene.show(primaryStage);

	    primaryStage.show();
    }
}
