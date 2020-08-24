package net.bb2.modroller.scenes;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import net.bb2.modroller.config.ModrollerConfig;
import org.eclipse.jgit.api.Git;

import javax.net.ssl.*;
import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;


public class GitUpdateTask implements Runnable {

	private final ModrollerConfig config;
	private final Label infoLabel;
	private final Callback callback;

	public GitUpdateTask(ModrollerConfig modrollerConfig, Label infoLabel, Callback callback) {
		this.config = modrollerConfig;
		this.infoLabel = infoLabel;
		this.callback = callback;
	}


	@Override
	public void run() {

		File modrollerDir;
		try {
			modrollerDir = config.getOrCreateModrollerDir();
		} catch (IOException e) {
			Platform.runLater(() -> {
				infoLabel.setText("Problem initialising directory: " + e.getMessage());
				infoLabel.setTextFill(Color.web("#993333"));
			});
			return;
		}
		try {
			trustAllCerts();

			File modRepoDir = modrollerDir.toPath().resolve("bb2modrepo").toFile();
			if (modRepoDir.exists()) {
				Git.open(modRepoDir)
						.pull()
						.call();
			} else {
				Git.cloneRepository()
						.setURI("https://github.com/bb2modder/bb2modrepo.git")
						.setDirectory(modRepoDir)
						.call();

			}
			config.setModRepoDir(modRepoDir);
		} catch (Exception e) {
			Platform.runLater(() -> {
				infoLabel.setText("Problem communicating with Git: " + e.getMessage());
				infoLabel.setTextFill(Color.web("#993333"));
			});
			e.printStackTrace();
			return;
		}


		Platform.runLater(callback::onAction);
	}

	private void trustAllCerts() throws NoSuchAlgorithmException, KeyManagementException {
		TrustManager[] trustAllCerts = new TrustManager[] {
				new X509TrustManager() {
					public java.security.cert.X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					public void checkClientTrusted(X509Certificate[] certs, String authType) {  }

					public void checkServerTrusted(X509Certificate[] certs, String authType) {  }

				}
		};

		SSLContext sc = SSLContext.getInstance("SSL");
		sc.init(null, trustAllCerts, new java.security.SecureRandom());
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

		// Create all-trusting host name verifier
		HostnameVerifier allHostsValid = new HostnameVerifier() {
			public boolean verify(String hostname, SSLSession session) {
				return true;
			}
		};
		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	}

}
