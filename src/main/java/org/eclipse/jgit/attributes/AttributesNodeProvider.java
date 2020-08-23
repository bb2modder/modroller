/*
 * Copyright (C) 2014, Arthur Daussy <arthur.daussy@obeo.fr> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.attributes;

import org.eclipse.jgit.lib.CoreConfig;

import java.io.IOException;

/**
 * An interface used to retrieve the global and info
 * {@link AttributesNode}s.
 *
 * @since 4.2
 */
public interface AttributesNodeProvider {

	/**
	 * Retrieve the {@link AttributesNode} that
	 * holds the information located in $GIT_DIR/info/attributes file.
	 *
	 * @return the {@link AttributesNode} that holds
	 *         the information located in $GIT_DIR/info/attributes file.
	 * @throws IOException
	 *             if an error is raised while parsing the attributes file
	 */
	AttributesNode getInfoAttributesNode() throws IOException;

	/**
	 * Retrieve the {@link AttributesNode} that
	 * holds the information located in the global gitattributes file.
	 *
	 * @return the {@link AttributesNode} that holds
	 *         the information located in the global gitattributes file.
	 * @throws IOException
	 *             java.io.IOException if an error is raised while parsing the
	 *             attributes file
	 * @see CoreConfig#getAttributesFile()
	 */
	AttributesNode getGlobalAttributesNode() throws IOException;

}
