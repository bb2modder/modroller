package net.bb2.modroller.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ModrollerConfig {

	private static ModrollerConfig instance = new ModrollerConfig();
	public static ModrollerConfig getInstance() {
		return instance;
	}

	private ModrollerConfig() {

	}

	private File modRepoDir;

	private File bb2Dir;

	private Set<String> installedMods = new LinkedHashSet<>();

	public File getBb2Dir() {
		return bb2Dir;
	}

	public void setBb2Dir(File bb2Dir) {
		this.bb2Dir = bb2Dir;
	}

	public File getOrCreateModrollerDir() throws IOException {
		if (bb2Dir == null) {
			return null;
		}

		File modrollerDir = bb2Dir.toPath().resolve("Modroller").toFile();
		if (!modrollerDir.exists()) {
			Files.createDirectories(modrollerDir.toPath());
		}
		return modrollerDir;
	}

	public File getOrCreateBackupDir() throws IOException {
		if (bb2Dir == null) {
			return null;
		}

		File backupDir = getOrCreateModrollerDir().toPath().resolve("backup").toFile();
		if (!backupDir.exists()) {
			Files.createDirectories(backupDir.toPath());
		}
		return backupDir;
	}

	public void setModRepoDir(File modRepoDir) {
		this.modRepoDir = modRepoDir;
	}

	public File getModRepoDir() {
		return modRepoDir;
	}

	public Set<String> getInstalledMods() throws IOException {
		installedMods.clear();
		File installedFile = getInstalledModsFile();
		if (installedFile.exists()) {
			List<String> fileContents = new ObjectMapper().readValue(installedFile, List.class);
			installedMods.addAll(fileContents);
		}
		return installedMods;
	}

	private File getInstalledModsFile() throws IOException {
		return getOrCreateModrollerDir().toPath().resolve("installed.json").toFile();
	}

	public void addInstalledMod(String modDirName) throws IOException {
		installedMods.add(modDirName);

		refreshFileContent();
	}

	public void removeInstalledMod(String modDirName) throws IOException {
		installedMods.remove(modDirName);

		refreshFileContent();
	}

	private void refreshFileContent() throws IOException {
		File installedModsFile = getInstalledModsFile();
		if (installedModsFile.exists()) {
			installedModsFile.delete();
		}
		new ObjectMapper().writeValue(installedModsFile, installedMods);
	}

}
