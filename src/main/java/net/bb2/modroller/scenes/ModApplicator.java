package net.bb2.modroller.scenes;

import javafx.scene.control.TextArea;
import net.bb2.modroller.config.ModInfo;

import java.io.File;

public class ModApplicator {
	private final TextArea textArea;

	public ModApplicator(TextArea textArea) {
		this.textArea = textArea;
	}

	public void install(File modDir, ModInfo modInfo) {
		textArea.appendText("Installing " + modInfo.getName() + "\n");
	}

	public void uninstall(File modDir, ModInfo modInfo) {
		textArea.appendText("Uninstalling " + modInfo.getName() + "\n");
	}
}
