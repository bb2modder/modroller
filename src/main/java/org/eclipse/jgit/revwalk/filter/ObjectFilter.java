/**
 * Copyright (C) 2015, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk.filter;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.ObjectWalk;

import java.io.IOException;

/**
 * Selects interesting objects when walking.
 * <p>
 * Applications should install the filter on an ObjectWalk by
 * {@link ObjectWalk#setObjectFilter(org.eclipse.jgit.revwalk.filter.ObjectFilter)}
 * prior to starting traversal.
 *
 * @since 4.0
 */
public abstract class ObjectFilter {
	/** Default filter that always returns true. */
	public static final org.eclipse.jgit.revwalk.filter.ObjectFilter ALL = new AllFilter();

	private static final class AllFilter extends org.eclipse.jgit.revwalk.filter.ObjectFilter {
		@Override
		public boolean include(ObjectWalk walker, AnyObjectId o) {
			return true;
		}
	}

	/**
	 * Determine if the named object should be included in the walk.
	 *
	 * @param walker
	 *            the active walker this filter is being invoked from within.
	 * @param objid
	 *            the object currently being tested.
	 * @return {@code true} if the named object should be included in the walk.
	 * @throws MissingObjectException
	 *             an object the filter needed to consult to determine its
	 *             answer was missing
	 * @throws IncorrectObjectTypeException
	 *             an object the filter needed to consult to determine its
	 *             answer was of the wrong type
	 * @throws IOException
	 *             an object the filter needed to consult to determine its
	 *             answer could not be read.
	 */
	public abstract boolean include(ObjectWalk walker, AnyObjectId objid)
			throws MissingObjectException, IncorrectObjectTypeException,
			       IOException;
}
