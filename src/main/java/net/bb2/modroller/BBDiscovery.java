package net.bb2.modroller;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BBDiscovery {

	private final static List<String> expectedDirsWindows = List.of("Program Files (x86)", "Steam", "steamapps", "common", "Blood Bowl 2");
	private final static List<String> expectedDirsMac = List.of("Library", "Application Support", "Steam", "steamapps", "common", "Blood Bowl 2");

	public Optional<File> findBB2Exe() {
		String executableFileName = OsCheck.IS_MAC_OS ? "BloodBowl2.app" : "BloodBowl2.exe";
		List<String> expectedDirs = OsCheck.IS_MAC_OS ? expectedDirsMac : expectedDirsWindows;

		for (File driveRoot : File.listRoots()) {
			Path cursor = driveRoot.toPath();
			if (OsCheck.IS_MAC_OS) {
				cursor = new File(System.getProperty("user.home")).toPath();
			}
			for (String expectedDir : expectedDirs) {
				cursor = cursor.resolve(expectedDir);
			}

			File possibleDir = cursor.toFile();
			if (possibleDir.exists() && possibleDir.isDirectory()) {
				File executable = cursor.resolve(executableFileName).toFile();
				if (executable.exists()) {
					return Optional.of(executable);
				}
			}

			// Try without Program Files
			cursor = driveRoot.toPath();
			ArrayList<String> trimmed = new ArrayList<>(expectedDirs);
			trimmed.remove(0);
			for (String expectedDir : trimmed) {
				cursor = cursor.resolve(expectedDir);
			}

			possibleDir = cursor.toFile();
			if (possibleDir.exists() && possibleDir.isDirectory()) {
				File executable = cursor.resolve(executableFileName).toFile();
				if (executable.exists()) {
					return Optional.of(executable);
				}
			}
		}

		return Optional.empty();
	}
}
