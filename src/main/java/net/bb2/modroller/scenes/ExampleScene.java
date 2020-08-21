package net.bb2.modroller.scenes;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

public class ExampleScene {

	private final Scene scene;

	public ExampleScene() {
		GridPane grid = new GridPane();
		grid.setPadding(new Insets(12,12,12,12));
		grid.setMinSize(400,200);

		Label numberLabel = new Label("Phone Number:");
		numberLabel.setMinWidth(100);
		numberLabel.setAlignment(Pos.CENTER_RIGHT);
		grid.add(numberLabel, 0, 0);

		TextField numberField = new TextField();
		numberLabel.setLabelFor(numberField);
		grid.add(numberField, 1, 0);

		Label messageLabel = new Label("Message:");
		messageLabel.setMinWidth(100);
		messageLabel.setAlignment(Pos.CENTER_RIGHT);
		grid.add(messageLabel, 0, 1);

		TextField messageField = new TextField();
		messageLabel.setLabelFor(messageField);
		grid.add(messageField, 1, 1);

		Button sendButton = new Button("Send SMS");
		sendButton.setOnAction(event -> {
			System.out.println("Button clicked?");
		});
		grid.add(sendButton, 1, 2);

		scene = new Scene(grid, 400, 200);
	}

	public Scene getScene() {
		return scene;
	}
}
