package net.bb2.modroller.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ModrollerConfig {

	private static ModrollerConfig instance = new ModrollerConfig();
	public static ModrollerConfig getInstance() {
		return instance;
	}

	private ModrollerConfig() {

	}

	private File modRepoDir;

	private File bb2Dir;

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

	public void setModRepoDir(File modRepoDir) {
		this.modRepoDir = modRepoDir;
	}

	public File getModRepoDir() {
		return modRepoDir;
	}
}
