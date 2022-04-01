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
import net.bb2.modroller.OsCheck;
import net.bb2.modroller.config.ModrollerConfig;

import java.io.File;

public class FindBaseDirScene extends ModRollerScene {

	private final GridPane grid;
	private final BBDiscovery bbDiscovery = new BBDiscovery();
	private final TextField bb2DirTextField;
	private final FileChooser fileChooser;
	private final Label validityLabel;
	private final Button dirPickerButton;

	private final Button processPackagesButton;
	private final String executableName;

	private File bbDirectory;
	private boolean bbDirectoryCorrect = false;

	public FindBaseDirScene() {
		grid = new GridPane();
		grid.setPadding(new Insets(12,12,12,12));

		Label bb2DirLabel = new Label("Select your Blood Bowl 2 executable:");
		grid.addRow(0, bb2DirLabel);


		bb2DirTextField = new TextField();
		bb2DirTextField.setEditable(false);
		bb2DirTextField.setPrefWidth(500);


		fileChooser = new FileChooser();
		executableName = OsCheck.IS_MAC_OS ? "BloodBowl2.app" : "BloodBowl2.exe";
		fileChooser.setInitialFileName(executableName);
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Executable", executableName));

		dirPickerButton = new Button("Find...");

		grid.addRow(1, bb2DirTextField, dirPickerButton);

		validityLabel = new Label();
		grid.addRow(2, validityLabel);

		processPackagesButton = new Button();
		processPackagesButton.setText("Process data packages");
		processPackagesButton.setVisible(false);
		processPackagesButton.setOnAction(event -> {
			if (completionHandler != null) {
				completionHandler.onAction();
			}
		});

		grid.addRow(3, processPackagesButton);

		this.scene = new Scene(grid, SceneDefaults.WIDTH, SceneDefaults.HEIGHT);

		bbDiscovery.findBB2Exe().ifPresent(this::bbExeChanged);
	}

	public void initialise(Stage primaryStage) {
		dirPickerButton.setOnAction(event -> {
			File file = fileChooser.showOpenDialog(primaryStage);
			if (file != null) {
				bbExeChanged(file);
			}
		});
	}

	private void bbExeChanged(File bb2Exe) {
		bb2DirTextField.setText(bb2Exe.getAbsolutePath());
		bbDirectory = bb2Exe.getParentFile();
		fileChooser.setInitialDirectory(bbDirectory);

		setDirCorrect(bb2Exe.getName().equals(executableName));
	}

	private void setDirCorrect(boolean isCorrect) {
		this.bbDirectoryCorrect = isCorrect;
		if (isCorrect) {
			ModrollerConfig.getInstance().setBb2Dir(bbDirectory);
			validityLabel.setText("BloodBowl2.exe found successfully");
			validityLabel.setTextFill(Color.web("#339933"));
			processPackagesButton.setVisible(true);
		} else {
			validityLabel.setText("You must select your Blood Bowl 2 executable to proceed");
			validityLabel.setTextFill(Color.web("#993333"));
			processPackagesButton.setVisible(false);
		}
	}
}
