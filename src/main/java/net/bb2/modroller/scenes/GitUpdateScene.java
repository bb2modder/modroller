package net.bb2.modroller.scenes;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import net.bb2.modroller.config.ModrollerConfig;

public class GitUpdateScene extends ModRollerScene {

	private final GridPane grid;
	private final Label infoLabel;

	public GitUpdateScene() {

		grid = new GridPane();
		grid.setPadding(new Insets(12, 12, 12, 12));

		infoLabel = new Label("Refreshing mods from server, please stand by");
		grid.addRow(0, infoLabel);

		this.scene = new Scene(grid, SceneDefaults.WIDTH, SceneDefaults.HEIGHT);
	}

	@Override
	public void show(Stage primaryStage) {
		super.show(primaryStage);
		process();
	}

	private void process() {
		GitUpdateTask gitUpdateTask = new GitUpdateTask(ModrollerConfig.getInstance(), infoLabel, () -> {
			if (completionHandler != null) {
				completionHandler.onAction();
			}
		});

		Thread thread = new Thread(gitUpdateTask);
		thread.setDaemon(true);
		thread.start();
	}

}
