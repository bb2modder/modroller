package net.bb2.modroller.scenes;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import net.bb2.modroller.config.ModInfo;
import net.bb2.modroller.config.ModParser;
import net.bb2.modroller.config.ModrollerConfig;

import java.io.File;
import java.util.*;

public class ModManagerScene extends ModRollerScene {

	private final TreeView treeView;
	private final TreeItem rootTreeItem;
	private final TextArea textArea;
	private Insets leftPad20 = new Insets(5, 0, 5, 20);
	private final ModApplicator modApplicator;

	private Map<String, GridPane> groups = new LinkedHashMap<>();

	public ModManagerScene() {
		treeView = new TreeView();
		treeView.setPrefWidth(SceneDefaults.WIDTH);
		treeView.setPrefHeight(SceneDefaults.HEIGHT);
		treeView.setPadding(new Insets(12, 12, 12, 12));

		rootTreeItem = new TreeItem("Mod listing");
		treeView.setRoot(rootTreeItem);

		ScrollPane scrollPane = new ScrollPane();
		scrollPane.setPrefSize(SceneDefaults.WIDTH, SceneDefaults.HEIGHT);
		scrollPane.setContent(treeView);

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
		rootTreeItem.getChildren().clear();
		rootTreeItem.setExpanded(true);

		try {
			Set<String> installedMods = ModrollerConfig.getInstance().getInstalledMods();
			Map<File, ModInfo> repoMods = new ModParser().getRepoMods();
			ArrayList<File> modDirs = new ArrayList<>(repoMods.keySet());
			modDirs.sort(Comparator.comparing(File::getName));

			for (File modDir : modDirs) {
				ModInfo modInfo = repoMods.get(modDir);

				String category = modInfo.getCategory();
				GridPane grid = groups.computeIfAbsent(category, a -> new GridPane());

				CheckBox checkbox = new CheckBox();
				checkbox.setPadding(leftPad20);
				checkbox.selectedProperty().setValue(installedMods.contains(modDir.getName()));
				checkbox.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
					try {

						if (!aBoolean) {
							modApplicator.install(modDir, modInfo);
						} else {
							modApplicator.uninstall(modDir, modInfo);
						}
					} catch (Exception e) {
						textArea.appendText("Error: " + e.getMessage() + "\n");
						System.err.println(e);
					}
				});
				Label nameLabel = new Label(modInfo.getName());
				nameLabel.setStyle("-fx-font-weight: bold");
				nameLabel.setPadding(leftPad20);
				Label descriptionLabel = new Label(modInfo.getDescription());
				descriptionLabel.setPadding(leftPad20);

				grid.addRow(grid.getRowCount(), checkbox, nameLabel, descriptionLabel);

				if (modInfo.getPreviewImage() != null && modInfo.getPreviewImage().length() > 0) {
					Button previewButton = new Button("Preview");
					descriptionLabel.setPadding(new Insets(5, 20, 5, 20));

					File previewFile = modDir.toPath().resolve(modInfo.getPreviewImage()).toFile();
					Image previewImage = new Image(previewFile.toURI().toString());
					ImageView imageView = new ImageView(previewImage);

					StackPane stackPane = new StackPane();
					stackPane.getChildren().add(imageView);

					Scene previewScene = new Scene(stackPane);

					Stage previewWindow = new Stage();
					previewWindow.setTitle(modInfo.getName() + " preview");
					previewWindow.setScene(previewScene);

					previewButton.setOnAction(event -> {
						previewWindow.show();
					});

					grid.add(previewButton, 3, grid.getRowCount() - 1);
				}

			}

			List<String> groupNames = new ArrayList<>(groups.keySet());
			groupNames.sort(String::compareTo);
			for (String groupName : groupNames) {
				TreeItem groupItem = new TreeItem(groupName);
				groupItem.getChildren().add(new TreeItem<>(groups.get(groupName)));
				rootTreeItem.getChildren().add(groupItem);
			}


		} catch (Exception e) {
			textArea.appendText("Error parsing mods: " + e.getMessage() + "\n");
			textArea.appendText("You may need to upgrade to a newer version of Modroller\n");
			System.err.println(e);
		}


	}

}
