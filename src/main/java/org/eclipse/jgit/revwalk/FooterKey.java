/*
 * Copyright (C) 2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import org.eclipse.jgit.lib.Constants;

import java.util.Locale;

/**
 * Case insensitive key for a {@link org.eclipse.jgit.revwalk.FooterLine}.
 */
public final class FooterKey {
	/** Standard {@code Signed-off-by} */
	public static final org.eclipse.jgit.revwalk.FooterKey SIGNED_OFF_BY = new org.eclipse.jgit.revwalk.FooterKey("Signed-off-by"); //$NON-NLS-1$

	/** Standard {@code Acked-by} */
	public static final org.eclipse.jgit.revwalk.FooterKey ACKED_BY = new org.eclipse.jgit.revwalk.FooterKey("Acked-by"); //$NON-NLS-1$

	/** Standard {@code CC} */
	public static final org.eclipse.jgit.revwalk.FooterKey CC = new org.eclipse.jgit.revwalk.FooterKey("CC"); //$NON-NLS-1$

	private final String name;

	final byte[] raw;

	/**
	 * Create a key for a specific footer line.
	 *
	 * @param keyName
	 *            name of the footer line.
	 */
	public FooterKey(String keyName) {
		name = keyName;
		raw = Constants.encode(keyName.toLowerCase(Locale.ROOT));
	}

	/**
	 * Get name of this footer line.
	 *
	 * @return name of this footer line.
	 */
	public String getName() {
		return name;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "FooterKey[" + name + "]";
	}
}
