package net.bb2.modroller.scenes;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import net.bb2.modroller.config.ModInfo;
import net.bb2.modroller.config.ModParser;
import net.bb2.modroller.config.ModrollerConfig;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class ModManagerScene extends ModRollerScene {

	private final GridPane grid;
	private final TextArea textArea;
	private Insets leftPad20 = new Insets(5, 0, 5, 20);
	private final ModApplicator modApplicator;

	public ModManagerScene() {


		grid = new GridPane();
		grid.setPadding(new Insets(12, 12, 12, 12));

		ScrollPane scrollPane = new ScrollPane();
		scrollPane.setPrefSize(SceneDefaults.WIDTH, SceneDefaults.HEIGHT);
		scrollPane.setContent(grid);

		textArea = new TextArea();
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
			Set<String> installedMods = ModrollerConfig.getInstance().getInstalledMods();
			Map<File, ModInfo> repoMods = new ModParser().getRepoMods();

			for (Map.Entry<File, ModInfo> modEntry : repoMods.entrySet()) {

				CheckBox checkbox = new CheckBox();
				checkbox.setPadding(leftPad20);
				checkbox.selectedProperty().setValue(installedMods.contains(modEntry.getKey().getName()));
				checkbox.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
					try {

						if (!aBoolean) {
							modApplicator.install(modEntry.getKey(), modEntry.getValue());
						} else {
							modApplicator.uninstall(modEntry.getKey(), modEntry.getValue());
						}
					} catch (Exception e) {
						textArea.appendText("Error: " + e.getMessage() + "\n");
						System.err.println(e);
					}
				});
				Label nameLabel = new Label(modEntry.getValue().getName());
				nameLabel.setStyle("-fx-font-weight: bold");
				nameLabel.setPadding(leftPad20);
				Label descriptionLabel = new Label(modEntry.getValue().getDescription());
				descriptionLabel.setPadding(leftPad20);

				grid.addRow(cursor, checkbox, nameLabel, descriptionLabel);

				if (modEntry.getValue().getPreviewImage() != null && modEntry.getValue().getPreviewImage().length() > 0) {
					Button previewButton = new Button("Preview");
					descriptionLabel.setPadding(new Insets(5, 20, 5, 20));

					File previewFile = modEntry.getKey().toPath().resolve(modEntry.getValue().getPreviewImage()).toFile();
					Image previewImage = new Image(previewFile.toURI().toString());
					ImageView imageView = new ImageView(previewImage);

					StackPane stackPane = new StackPane();
					stackPane.getChildren().add(imageView);

					Scene previewScene = new Scene(stackPane);

					Stage previewWindow = new Stage();
					previewWindow.setTitle(modEntry.getValue().getName() + " preview");
					previewWindow.setScene(previewScene);

					previewButton.setOnAction(event -> {
						previewWindow.show();
					});

					grid.add(previewButton, 3, cursor);
				}

				cursor++;
			}

		} catch (Exception e) {
			Label errorLabel = new Label("Error parsing mods: " + e.getMessage());
			errorLabel.setTextFill(Color.web("#993333"));
			grid.addRow(cursor + 1, errorLabel);
			System.err.println(e);
		}


	}

}
