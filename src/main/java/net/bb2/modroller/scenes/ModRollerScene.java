package net.bb2.modroller.scenes;

import javafx.scene.Scene;
import javafx.stage.Stage;

public abstract class ModRollerScene {

	protected Scene scene;
	protected Callback completionHandler;

	public void show(Stage primaryStage) {
		primaryStage.setScene(this.scene);
	}

	public void setOnComplete(Callback completionHandler) {
		this.completionHandler = completionHandler;
	}

}
