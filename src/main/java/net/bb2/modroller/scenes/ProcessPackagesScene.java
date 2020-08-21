package net.bb2.modroller.scenes;

import com.google.inject.Inject;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import net.bb2.modroller.config.ModrollerConfig;

public class ProcessPackagesScene extends ModRollerScene {


	private final ModrollerConfig config;
	private final GridPane grid;

	@Inject
	public ProcessPackagesScene(ModrollerConfig config) {
		this.config = config;

		grid = new GridPane();
		grid.setPadding(new Insets(12,12,12,12));

		this.scene = new Scene(grid, SceneDefaults.WIDTH, SceneDefaults.HEIGHT);
	}

	@Override
	public void show(Stage primaryStage) {
		super.show(primaryStage);

		System.out.println("Showing process packages");
	}

}
