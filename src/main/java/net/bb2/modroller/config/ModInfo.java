package net.bb2.modroller.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModInfo {

	private String name;
	private String category;
	private String description;
	private String previewImage;

	private Map<String, String> files = new LinkedHashMap<>(); // Mapping of filename to path within data dir
	private Map<String, Map<String, String>> xml = new LinkedHashMap<>();

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getPreviewImage() {
		return previewImage;
	}

	public void setPreviewImage(String previewImage) {
		this.previewImage = previewImage;
	}

	public Map<String, String> getFiles() {
		return files;
	}

	public void setFiles(Map<String, String> files) {
		this.files = files;
	}

	public Map<String, Map<String, String>> getXml() {
		return xml;
	}

	public void setXml(Map<String, Map<String, String>> xml) {
		this.xml = xml;
	}

	public String getCategory() {
		if (category == null || category.length() == 0) {
			return "Uncategorised";
		} else {
			return category;
		}
	}

	public void setCategory(String category) {
		this.category = category;
	}
}
