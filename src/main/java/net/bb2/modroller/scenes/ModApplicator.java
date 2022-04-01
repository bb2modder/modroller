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
	private final ModXmlApplicator modXmlApplicator = new ModXmlApplicator();

	public ModApplicator(TextArea textArea) {
		this.textArea = textArea;
	}

	public void install(File modDir, ModInfo modInfo) throws Exception {
		textArea.appendText("Installing " + modInfo.getName() + "\n");
		File bb2Dir = ModrollerConfig.getInstance().getBb2Dir();
		Path dataDir = bb2Dir.toPath().resolve("Data");
		File backupDir = ModrollerConfig.getInstance().getOrCreateBackupDir();

		if (modInfo.getFiles() != null) {
			for (Map.Entry<String, String> fileEntry : modInfo.getFiles().entrySet()) {
				String filename = fileEntry.getKey();
				String replacementPath = fileEntry.getValue();

				File targetFile = dataDir.resolve(replacementPath).resolve(filename).toFile();
				if (targetFile.exists()) {
					backup(targetFile, backupDir, filename, replacementPath);
				} else {
					textArea.appendText("No existing file at " + targetFile + " to back up\n");
				}

				Path sourceFile = modDir.toPath().resolve(filename);

				textArea.appendText("Copying " + filename + " to " + targetFile.getAbsolutePath() + "\n");
				Files.copy(sourceFile, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		}

		if (modInfo.getXml() != null) {
			for (String xmlFilePath : modInfo.getXml().keySet()) {
				File targetFile = dataDir.resolve(xmlFilePath).toFile();
				if (!targetFile.exists()) {
					throw new IOException("Could not find file " + targetFile.getAbsolutePath() + " defined in " + modInfo.getName());
				}

				String replacementPath = "";
				if (xmlFilePath.contains("/")) {
					replacementPath = xmlFilePath.substring(0 , xmlFilePath.lastIndexOf('/'));
				}
				backup(targetFile, backupDir, targetFile.getName(), replacementPath);

				textArea.appendText("Replacing xml snippets within " + targetFile.getAbsolutePath() + "\n");
				modXmlApplicator.apply(targetFile, modDir, modInfo.getXml().get(xmlFilePath));
			}
		}

		textArea.appendText("Installed " + modInfo.getName() + " successfully\n");
		ModrollerConfig.getInstance().addInstalledMod(modDir.getName());
	}

	public void uninstall(File modDir, ModInfo modInfo) throws Exception {
		textArea.appendText("Uninstalling " + modInfo.getName() + "\n");
		File bb2Dir = ModrollerConfig.getInstance().getBb2Dir();
		Path dataDir = bb2Dir.toPath().resolve("Data");
		File backupDir = ModrollerConfig.getInstance().getOrCreateBackupDir();

		if (modInfo.getFiles() != null) {
			for (Map.Entry<String, String> fileEntry : modInfo.getFiles().entrySet()) {
				String filename = fileEntry.getKey();
				String replacementPath = fileEntry.getValue();

				File backupFile = backupDir.toPath().resolve(replacementPath).resolve(filename).toFile();
				if (!backupFile.exists()) {
					textArea.appendText("Warning: No backup at " + backupFile.getAbsolutePath() + "\n");
				} else {
					File targetFile = dataDir.resolve(replacementPath).resolve(filename).toFile();
					textArea.appendText("Copying backup of " + filename + " to " + targetFile.getAbsolutePath() + "\n");
					Files.copy(backupFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}


		if (modInfo.getXml() != null) {
			for (String xmlFilePath : modInfo.getXml().keySet()) {
				File targetFile = dataDir.resolve(xmlFilePath).toFile();
				if (!targetFile.exists()) {
					throw new IOException("Could not find file " + targetFile.getAbsolutePath() + " defined in " + modInfo.getName());
				}


				File backupFile = backupDir.toPath().resolve(xmlFilePath).toFile();
				if (!backupFile.exists()) {
					textArea.appendText("Error: Can not find backup at " + backupFile.getAbsolutePath() + "\n");
				} else {
					textArea.appendText("Rolling back XML changes to " + targetFile.getAbsolutePath() + "\n");

					modXmlApplicator.remove(targetFile, backupFile, modInfo.getXml().get(xmlFilePath).keySet());
				}
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
