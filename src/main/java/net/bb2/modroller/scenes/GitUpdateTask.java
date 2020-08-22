package net.bb2.modroller.scenes;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import net.bb2.modroller.config.ModrollerConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;

public class GitUpdateTask implements Runnable {

	private final ModrollerConfig config;
	private final Label infoLabel;
	private final Callback callback;

	public GitUpdateTask(ModrollerConfig modrollerConfig, Label infoLabel, Callback callback) {
		this.config = modrollerConfig;
		this.infoLabel = infoLabel;
		this.callback = callback;
	}


	@Override
	public void run() {

		File modrollerDir;
		try {
			modrollerDir = config.getOrCreateModrollerDir();
		} catch (IOException e) {
			Platform.runLater(() -> {
				infoLabel.setText("Problem initialising directory: " + e.getMessage());
				infoLabel.setTextFill(Color.web("#993333"));
			});
			return;
		}
		try {

			File modRepoDir = modrollerDir.toPath().resolve("bb2modrepo").toFile();
			if (modRepoDir.exists()) {
				Git.open(modRepoDir)
						.pull()
						.call();
			} else {
				Git.cloneRepository()
						.setURI("https://github.com/bb2modder/bb2modrepo.git")
						.setDirectory(modRepoDir)
						.call();

			}
			config.setModRepoDir(modRepoDir);
		} catch (GitAPIException | IOException e) {
			Platform.runLater(() -> {
				infoLabel.setText("Problem communicating with Git: " + e.getMessage());
				infoLabel.setTextFill(Color.web("#993333"));
			});
			return;
		}


		Platform.runLater(callback::onAction);
	}

}
