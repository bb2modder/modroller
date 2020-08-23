/*
 * Copyright (C) 2013, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

/**
 * A pack file extension.
 */
public class PackExt {
	private static volatile org.eclipse.jgit.internal.storage.pack.PackExt[] VALUES = new org.eclipse.jgit.internal.storage.pack.PackExt[] {};

	/** A pack file extension. */
	public static final org.eclipse.jgit.internal.storage.pack.PackExt PACK = newPackExt("pack"); //$NON-NLS-1$

	/** A pack index file extension. */
	public static final org.eclipse.jgit.internal.storage.pack.PackExt INDEX = newPackExt("idx"); //$NON-NLS-1$

	/** A keep pack file extension. */
	public static final org.eclipse.jgit.internal.storage.pack.PackExt KEEP = newPackExt("keep"); //$NON-NLS-1$

	/** A pack bitmap index file extension. */
	public static final org.eclipse.jgit.internal.storage.pack.PackExt BITMAP_INDEX = newPackExt("bitmap"); //$NON-NLS-1$

	/** A reftable file. */
	public static final org.eclipse.jgit.internal.storage.pack.PackExt REFTABLE = newPackExt("ref"); //$NON-NLS-1$

	/**
	 * Get all of the PackExt values.
	 *
	 * @return all of the PackExt values.
	 */
	public static org.eclipse.jgit.internal.storage.pack.PackExt[] values() {
		return VALUES;
	}

	/**
	 * Returns a PackExt for the file extension and registers it in the values
	 * array.
	 *
	 * @param ext
	 *            the file extension.
	 * @return the PackExt for the ext
	 */
	public static synchronized org.eclipse.jgit.internal.storage.pack.PackExt newPackExt(String ext) {
		org.eclipse.jgit.internal.storage.pack.PackExt[] dst = new org.eclipse.jgit.internal.storage.pack.PackExt[VALUES.length + 1];
		for (int i = 0; i < VALUES.length; i++) {
			org.eclipse.jgit.internal.storage.pack.PackExt packExt = VALUES[i];
			if (packExt.getExtension().equals(ext))
				return packExt;
			dst[i] = packExt;
		}
		if (VALUES.length >= 32)
			throw new IllegalStateException(
					"maximum number of pack extensions exceeded"); //$NON-NLS-1$

		org.eclipse.jgit.internal.storage.pack.PackExt value = new org.eclipse.jgit.internal.storage.pack.PackExt(ext, VALUES.length);
		dst[VALUES.length] = value;
		VALUES = dst;
		return value;
	}

	private final String ext;

	private final int pos;

	private PackExt(String ext, int pos) {
		this.ext = ext;
		this.pos = pos;
	}

	/**
	 * Get the file extension.
	 *
	 * @return the file extension.
	 */
	public String getExtension() {
		return ext;
	}

	/**
	 * Get the position of the extension in the values array.
	 *
	 * @return the position of the extension in the values array.
	 */
	public int getPosition() {
		return pos;
	}

	/**
	 * Get the bit mask of the extension e.g {@code 1 << getPosition()}.
	 *
	 * @return the bit mask of the extension e.g {@code 1 << getPosition()}.
	 */
	public int getBit() {
		return 1 << getPosition();
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		return String.format("PackExt[%s, bit=0x%s]", getExtension(), //$NON-NLS-1$
				Integer.toHexString(getBit()));
	}
}
