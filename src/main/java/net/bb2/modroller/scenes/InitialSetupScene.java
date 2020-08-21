package net.bb2.modroller.scenes;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import net.bb2.modroller.BBDiscovery;

import java.io.File;

public class InitialSetupScene {

	private final Scene scene;
	private final GridPane grid;
	private final BBDiscovery bbDiscovery = new BBDiscovery();
	private final TextField bb2DirTextField;
	private final FileChooser fileChooser;
	private final Label validityLabel;

	private File bbDirectory;
	private boolean bbDirectoryCorrect = false;

	public InitialSetupScene(Stage primaryStage) {
		grid = new GridPane();
		grid.setPadding(new Insets(12,12,12,12));

		Label bb2DirLabel = new Label("Select your Blood Bowl 2 executable:");
		grid.addRow(0, bb2DirLabel);


		bb2DirTextField = new TextField();
		bb2DirTextField.setEditable(false);
		bb2DirTextField.setPrefWidth(500);


		fileChooser = new FileChooser();
		fileChooser.setInitialFileName("BloodBowl2.exe");
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Executable", "BloodBowl2.exe"));

		Button dirPickerButton = new Button("Find...");
		dirPickerButton.setOnAction(event -> {
			File file = fileChooser.showOpenDialog(primaryStage);
			if (file != null) {
				bbDirChanged(file);
			}
		});

		grid.addRow(1, bb2DirTextField, dirPickerButton);

		validityLabel = new Label();
		grid.addRow(2, validityLabel);

		this.scene = new Scene(grid, SceneDefaults.WIDTH, SceneDefaults.HEIGHT);

		bbDiscovery.findBB2Exe().ifPresent(this::bbDirChanged);
	}

	public Scene getScene() {
		return scene;
	}

	private void bbDirChanged(File bb2Exe) {
		this.bbDirectory = bb2Exe;
		bb2DirTextField.setText(bb2Exe.getAbsolutePath());
		bbDirectory = bb2Exe.getParentFile();
		fileChooser.setInitialDirectory(bbDirectory);

		setDirCorrect(bb2Exe.getName().equals("BloodBowl2.exe"));
	}

	private void setDirCorrect(boolean isCorrect) {
		this.bbDirectoryCorrect = isCorrect;
		if (isCorrect) {
			validityLabel.setText("BloodBowl2.exe found successfully");
			validityLabel.setTextFill(Color.web("#339933"));
		} else {
			validityLabel.setText("You must select your Blood Bowl 2 executable to proceed");
			validityLabel.setTextFill(Color.web("#993333"));
		}
	}
}
