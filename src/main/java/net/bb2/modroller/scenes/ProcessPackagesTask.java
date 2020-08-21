package net.bb2.modroller.scenes;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ProcessPackagesTask implements Runnable {

	private final File baseDir;
	private final TextArea textArea;
	private final Label currentProgressLabel;
	private final ProgressBar progressBar;
	private final Callback callback;

	public ProcessPackagesTask(File bb2Dir, TextArea textArea, Label currentProgressLabel, ProgressBar progressBar, Callback callback) {
		this.baseDir = bb2Dir;
		this.textArea = textArea;
		this.currentProgressLabel = currentProgressLabel;
		this.progressBar = progressBar;
		this.callback = callback;
	}

	@Override
	public void run() {
		progressBar.setProgress(0f);

		File dataDir = baseDir.toPath().resolve("Data").toFile();
		if (!dataDir.exists()) {
			Platform.runLater(() -> {
				currentProgressLabel.setText("Can not find data directory");
			});
			return;
		}
		File packagesDir = dataDir.toPath().resolve("Packages").toFile();
		if (!packagesDir.exists()) {
			Platform.runLater(() -> {
				currentProgressLabel.setText("Can not find packages directory");
			});
			return;
		}

		List<File> packageFiles = new ArrayList<>();
		for (File packageFile : packagesDir.listFiles()) {
			if (packageFile.getName().endsWith(".cpk")) {
				packageFiles.add(packageFile);
			}
		}

		File quickBms = new File("assets/bms/quickbms.exe");
		if (!quickBms.exists()) {
			Platform.runLater(() -> {
				currentProgressLabel.setText("Can not find quickbms.exe");
			});
			return;
		}
		File bb2Bms = new File("assets/bms/bloodbowl2.bms");
		if (!quickBms.exists()) {
			Platform.runLater(() -> {
				currentProgressLabel.setText("Can not find bloodbowl2.bms");
			});
			return;
		}

		try {
			float cursor = 0;
			for (File packageFile : packageFiles) {
				Platform.runLater(() -> {
					currentProgressLabel.setText("Processing " + packageFile.getName());
				});
				Process process = new ProcessBuilder(quickBms.getAbsolutePath(), "-o", bb2Bms.getAbsolutePath(), packageFile.getAbsolutePath(), baseDir.getAbsolutePath()).start();


				BufferedReader processInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String s = null;
				while ((s = processInput.readLine()) != null) {
					final String currentline = s;
					Platform.runLater(() -> {
						textArea.appendText(currentline);
						textArea.appendText("\n");
					});
				}

				Files.delete(packageFile.toPath());
				cursor += 1f;
				final float displayCursor = cursor;
				Platform.runLater(() -> {
					progressBar.setProgress(displayCursor / (float)packageFiles.size());
				});
			}
		} catch (IOException e) {
			Platform.runLater(() -> {
				currentProgressLabel.setText("Error while processing: " + e.getMessage());
				currentProgressLabel.setTextFill(Color.web("#993333"));
			});
		}

		Platform.runLater(callback::onAction);
	}

}
