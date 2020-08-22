package net.bb2.modroller.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModParser {

	private final ObjectMapper objectMapper = new ObjectMapper();

	public ModParser() {

	}

	/**
	 * @return Mapping of mod directory within repo to parsed mod info JSON
	 */
	public Map<File, ModInfo> getRepoMods() throws IOException {
		Map<File, ModInfo> results = new LinkedHashMap<>();

		File modRepoDir = ModrollerConfig.getInstance().getModRepoDir();

		Files.list(modRepoDir.toPath()).forEach(modPath -> {
			try {
				File modDir = modPath.toFile();
				if (modDir.isDirectory()) {
					File jsonFile = modPath.resolve("mod.json").toFile();
					if (jsonFile.exists() && jsonFile.isFile()) {
						ModInfo modInfo = objectMapper.readValue(Files.readString(jsonFile.toPath()), ModInfo.class);
						if (modInfo.getName() != null && modInfo.getDescription() != null) {
							results.put(modDir, modInfo);
						}
					}
				}
			} catch (IOException e) {
				System.err.println(e.getMessage());
			}

		});

		return results;
	}
}
