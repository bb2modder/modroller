/*
 * Copyright (C) 2011, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.blame;

/**
 * Region of the result that still needs to be computed.
 * <p>
 * Regions are held in a singly-linked-list by {@link Candidate} using the
 * {@link Candidate#regionList} field. The list is kept in sorted order by
 * {@link #resultStart}.
 */
class Region {
	/** Next entry in the region linked list. */
	org.eclipse.jgit.blame.Region next;

	/** First position of this region in the result file blame is computing. */
	int resultStart;

	/** First position in the {@link Candidate} that owns this Region. */
	int sourceStart;

	/** Length of the region, always >= 1. */
	int length;

	Region(int rs, int ss, int len) {
		resultStart = rs;
		sourceStart = ss;
		length = len;
	}

	/**
	 * Copy the entire result region, but at a new source position.
	 *
	 * @param newSource
	 *            the new source position.
	 * @return the same result region, but offset for a new source.
	 */
	org.eclipse.jgit.blame.Region copy(int newSource) {
		return new org.eclipse.jgit.blame.Region(resultStart, newSource, length);
	}

	/**
	 * Split the region, assigning a new source position to the first half.
	 *
	 * @param newSource
	 *            the new source position.
	 * @param newLen
	 *            length of the new region.
	 * @return the first half of the region, at the new source.
	 */
	org.eclipse.jgit.blame.Region splitFirst(int newSource, int newLen) {
		return new org.eclipse.jgit.blame.Region(resultStart, newSource, newLen);
	}

	/**
	 * Edit this region to remove the first {@code d} elements.
	 *
	 * @param d
	 *            number of elements to remove from the start of this region.
	 */
	void slideAndShrink(int d) {
		resultStart += d;
		sourceStart += d;
		length -= d;
	}

	org.eclipse.jgit.blame.Region deepCopy() {
		org.eclipse.jgit.blame.Region head = new org.eclipse.jgit.blame.Region(resultStart, sourceStart, length);
		org.eclipse.jgit.blame.Region tail = head;
		for (org.eclipse.jgit.blame.Region n = next; n != null; n = n.next) {
			org.eclipse.jgit.blame.Region q = new org.eclipse.jgit.blame.Region(n.resultStart, n.sourceStart, n.length);
			tail.next = q;
			tail = q;
		}
		return head;
	}

	/** {@inheritDoc} */
	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		org.eclipse.jgit.blame.Region r = this;
		do {
			if (r != this)
				buf.append(',');
			buf.append(r.resultStart);
			buf.append('-');
			buf.append(r.resultStart + r.length);
			r = r.next;
		} while (r != null);
		return buf.toString();
	}
}
