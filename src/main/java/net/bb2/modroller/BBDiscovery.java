package net.bb2.modroller;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class BBDiscovery {

	private final static List<String> expectedDirs = List.of("Program Files (x86)", "Steam", "steamapps", "common", "Blood Bowl 2");

	public Optional<File> findBB2Exe() {
		for (File driveRoot : File.listRoots()) {
			Path cursor = driveRoot.toPath();
			for (String expectedDir : expectedDirs) {
				cursor = cursor.resolve(expectedDir);
			}

			File possibleDir = cursor.toFile();
			if (possibleDir.exists() && possibleDir.isDirectory()) {
				File executable = cursor.resolve("BloodBowl2.exe").toFile();
				if (executable.exists() && executable.isFile()) {
					return Optional.of(executable);
				}
			}
		}

		return Optional.empty();
	}
}
