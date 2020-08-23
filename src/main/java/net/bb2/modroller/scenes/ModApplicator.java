package net.bb2.modroller.scenes;

import javafx.scene.control.TextArea;
import net.bb2.modroller.config.ModInfo;
import net.bb2.modroller.config.ModrollerConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class ModApplicator {
	private final TextArea textArea;

	public ModApplicator(TextArea textArea) {
		this.textArea = textArea;
	}

	public void install(File modDir, ModInfo modInfo) throws IOException {
		textArea.appendText("Installing " + modInfo.getName() + "\n");
		File bb2Dir = ModrollerConfig.getInstance().getBb2Dir();
		Path dataDir = bb2Dir.toPath().resolve("Data");
		File backupDir = ModrollerConfig.getInstance().getOrCreateBackupDir();

		for (Map.Entry<String, String> fileEntry : modInfo.getFiles().entrySet()) {
			String filename = fileEntry.getKey();
			String replacementPath = fileEntry.getValue();

			File targetFile = dataDir.resolve(replacementPath).resolve(filename).toFile();
			backup(targetFile, backupDir, filename, replacementPath);

			Path sourceFile = modDir.toPath().resolve(filename);

			textArea.appendText("Copying " + filename + " to " + targetFile.getAbsolutePath() + "\n");
			Files.copy(sourceFile, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		textArea.appendText("Installed " + modInfo.getName() + " successfully\n");
		ModrollerConfig.getInstance().addInstalledMod(modDir.getName());
	}

	public void uninstall(File modDir, ModInfo modInfo) throws IOException {
		textArea.appendText("Uninstalling " + modInfo.getName() + "\n");
		File bb2Dir = ModrollerConfig.getInstance().getBb2Dir();
		Path dataDir = bb2Dir.toPath().resolve("Data");
		File backupDir = ModrollerConfig.getInstance().getOrCreateBackupDir();

		for (Map.Entry<String, String> fileEntry : modInfo.getFiles().entrySet()) {
			String filename = fileEntry.getKey();
			String replacementPath = fileEntry.getValue();

			File backupFile = backupDir.toPath().resolve(replacementPath).resolve(filename).toFile();
			if (!backupFile.exists()) {
				textArea.appendText("Error: Can not find backup at " + backupFile.getAbsolutePath() + "\n");
			} else {
				File targetFile = dataDir.resolve(replacementPath).resolve(filename).toFile();
				textArea.appendText("Copying backup of " + filename + " to " + targetFile.getAbsolutePath() + "\n");
				Files.copy(backupFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}

		textArea.appendText("Uninstalled " + modInfo.getName() + " successfully\n");
		ModrollerConfig.getInstance().removeInstalledMod(modDir.getName());
	}

	private void backup(File targetFile, File backupDir, String filename, String replacementPath) throws IOException {
		Path backupTargetDir = backupDir.toPath().resolve(replacementPath);
		File backupFile = backupTargetDir.resolve(filename).toFile();
		if (!backupFile.exists()) {
			textArea.appendText("Creating backup of " + filename + " to " + backupFile.getAbsolutePath() + "\n");
			Files.createDirectories(backupTargetDir);
			Files.copy(targetFile.toPath(), backupFile.toPath());
		}
	}
}
