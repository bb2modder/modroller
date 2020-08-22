package net.bb2.modroller.scenes;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import net.bb2.modroller.config.ModrollerConfig;

public class ProcessPackagesScene extends ModRollerScene {


	private final ModrollerConfig config;
	private final GridPane grid;
	private final ProgressBar progressBar;

	private Label currentProgressLabel;
	private TextArea textArea;

	public ProcessPackagesScene(ModrollerConfig config) {
		this.config = config;

		grid = new GridPane();
		grid.setPadding(new Insets(12, 12, 12, 12));

		Label label1 = new Label("The BB2 data packages are currently being decompressed, do not close this window. This may take some time, do not worry if it pauses for a while.");
		grid.addRow(0, label1);
		Label label2 = new Label("If something goes wrong, you may need to reinstall Blood Bowl 2.");
		grid.addRow(1, label2);


		progressBar = new ProgressBar(0f);
		progressBar.setPrefWidth(400);
		grid.addRow(2, progressBar);


		currentProgressLabel = new Label();
		grid.addRow(3, currentProgressLabel);

		textArea = new TextArea();
		textArea.setPrefWidth(650);
		textArea.setPrefHeight(400);
		grid.addRow(4, textArea);

		this.scene = new Scene(grid, SceneDefaults.WIDTH, SceneDefaults.HEIGHT);
	}

	@Override
	public void show(Stage primaryStage) {
		super.show(primaryStage);
		process();
	}

	private void process() {
		ProcessPackagesTask processPackagesTask = new ProcessPackagesTask(config.getBb2Dir(), textArea, currentProgressLabel, progressBar, () -> {
			if (completionHandler != null) {
				completionHandler.onAction();
			}
		});

		Thread thread = new Thread(processPackagesTask);
		thread.setDaemon(true);
		thread.start();
	}

}
