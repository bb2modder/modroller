package net.bb2.modroller.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;

@Singleton
public class ModrollerConfig {

	private File bb2Dir;

	@Inject
	public ModrollerConfig() {

	}

	public File getBb2Dir() {
		return bb2Dir;
	}

	public void setBb2Dir(File bb2Dir) {
		this.bb2Dir = bb2Dir;
	}
}
