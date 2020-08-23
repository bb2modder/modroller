package net.bb2.modroller.scenes;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.bb2.modroller.config.ModInfo;
import net.bb2.modroller.config.ModParser;

import java.io.File;
import java.util.Map;

public class ModManagerScene extends ModRollerScene {

	private final GridPane grid;
	private Insets leftPad20 = new Insets(5, 0, 5, 20);
	private final ModApplicator modApplicator;

	public ModManagerScene() {


		grid = new GridPane();
		grid.setPadding(new Insets(12, 12, 12, 12));

		ScrollPane scrollPane = new ScrollPane();
		scrollPane.setPrefSize(SceneDefaults.WIDTH, SceneDefaults.HEIGHT);
		scrollPane.setContent(grid);

		TextArea textArea = new TextArea();
		textArea.setEditable(false);

		GridPane outerGrid = new GridPane();
		outerGrid.addRow(0, scrollPane);
		outerGrid.addRow(1, textArea);

		this.scene = new Scene(outerGrid, SceneDefaults.WIDTH, SceneDefaults.HEIGHT);

		modApplicator = new ModApplicator(textArea);
	}

	@Override
	public void show(Stage primaryStage) {
		super.show(primaryStage);
		populate();
	}

	private void populate() {
		grid.getChildren().clear();

		int cursor = 0;
		try {
			Map<File, ModInfo> repoMods = new ModParser().getRepoMods();

			for (Map.Entry<File, ModInfo> modEntry : repoMods.entrySet()) {

				CheckBox checkbox = new CheckBox();
				checkbox.setPadding(leftPad20);
				checkbox.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
					if (!aBoolean) {
						modApplicator.install(modEntry.getKey(), modEntry.getValue());
					} else {
						modApplicator.uninstall(modEntry.getKey(), modEntry.getValue());
					}
				});
				Label nameLabel = new Label(modEntry.getValue().getName());
				nameLabel.setStyle("-fx-font-weight: bold");
				nameLabel.setPadding(leftPad20);
				Label descriptionLabel = new Label(modEntry.getValue().getDescription());
				descriptionLabel.setPadding(leftPad20);

				grid.addRow(cursor, checkbox, nameLabel, descriptionLabel);
				cursor++;
			}

		} catch (Exception e) {
			Label errorLabel = new Label("Error parsing mods: " + e.getMessage());
			errorLabel.setTextFill(Color.web("#993333"));
			grid.addRow(cursor + 1, errorLabel);
		}


	}

}
