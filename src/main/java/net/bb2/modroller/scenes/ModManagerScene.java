package net.bb2.modroller.scenes;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.bb2.modroller.config.ModInfo;
import net.bb2.modroller.config.ModParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class ModManagerScene extends ModRollerScene {

	private final GridPane grid;
	private Insets leftPad20 = new Insets(5, 0, 5, 20);

	public ModManagerScene() {

		grid = new GridPane();
		grid.setPadding(new Insets(12, 12, 12, 12));

		ScrollPane scrollPane = new ScrollPane();
		scrollPane.setPrefSize(SceneDefaults.WIDTH, SceneDefaults.HEIGHT);
		scrollPane.setContent(grid);

		this.scene = new Scene(scrollPane, SceneDefaults.WIDTH, SceneDefaults.HEIGHT);
	}

	@Override
	public void show(Stage primaryStage) {
		super.show(primaryStage);
		populate();
	}

	private void populate() {
		grid.getChildren().clear();

		try {
			Map<File, ModInfo> repoMods = new ModParser().getRepoMods();

			ArrayList<File> modDirs = new ArrayList<>(repoMods.keySet());

			int cursor = 0;
			for (Map.Entry<File, ModInfo> modEntry : repoMods.entrySet()) {

				CheckBox checkbox = new CheckBox();
				checkbox.setPadding(leftPad20);
				Label nameLabel = new Label(modEntry.getValue().getName());
				nameLabel.setStyle("-fx-font-weight: bold");
				nameLabel.setPadding(leftPad20);
				Label descriptionLabel = new Label(modEntry.getValue().getDescription());
				descriptionLabel.setPadding(leftPad20);

				grid.addRow(cursor, checkbox, nameLabel, descriptionLabel);
				cursor++;
			}

		} catch (IOException e) {
			Label errorLabel = new Label("Error parsing mods: " + e.getMessage());
			errorLabel.setTextFill(Color.web("#993333"));
			grid.addRow(0, errorLabel);
		}


	}

}
