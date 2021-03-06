module modroller {
	exports net.bb2.modroller;
	exports net.bb2.modroller.config;
	exports net.bb2.modroller.scenes;
	requires com.fasterxml.jackson.core;
	requires com.fasterxml.jackson.databind;
	requires javafx.controls;
	requires java.sql;
	requires java.xml;
	requires java.security.jgss;
	requires java.management;
	requires jdk.crypto.ec;
	uses org.eclipse.jgit.transport.SshSessionFactory;
}
