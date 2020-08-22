package net.bb2.modroller.config;

import java.io.File;

public class ModrollerConfig {

	private static ModrollerConfig instance = new ModrollerConfig();

	public static ModrollerConfig getInstance() {
		return instance;
	}

	private ModrollerConfig() {

	}

	private File bb2Dir;

	public File getBb2Dir() {
		return bb2Dir;
	}

	public void setBb2Dir(File bb2Dir) {
		this.bb2Dir = bb2Dir;
	}
}
