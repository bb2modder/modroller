package net.bb2.modroller.scenes;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

public class CompletedScene extends ModRollerScene {


	private final GridPane grid;

	private Label currentProgressLabel;

	public CompletedScene() {
		grid = new GridPane();
		grid.setPadding(new Insets(12, 12, 12, 12));

		currentProgressLabel = new Label("All done! Go and mod your extracted files!");
		grid.addRow(3, currentProgressLabel);

		this.scene = new Scene(grid, SceneDefaults.WIDTH, SceneDefaults.HEIGHT);
	}

}
