/*
 * Copyright (C) 2010, Sasa Zivkov <sasa.zivkov@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.notes;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ThreeWayMerger;

import java.io.IOException;

/**
 * Three-way note tree merge.
 * <p>
 * Direct implementation of NoteMap merger without using
 * {@link org.eclipse.jgit.treewalk.TreeWalk} and
 * {@link org.eclipse.jgit.treewalk.AbstractTreeIterator}
 */
public class NoteMapMerger {
	private static final FanoutBucket EMPTY_FANOUT = new FanoutBucket(0);

	private static final org.eclipse.jgit.notes.LeafBucket EMPTY_LEAF = new org.eclipse.jgit.notes.LeafBucket(0);

	private final Repository db;

	private final NoteMerger noteMerger;

	private final MergeStrategy nonNotesMergeStrategy;

	private final ObjectReader reader;

	private final ObjectInserter inserter;

	private final MutableObjectId objectIdPrefix;

	/**
	 * Constructs a NoteMapMerger with custom
	 * {@link NoteMerger} and custom
	 * {@link MergeStrategy}.
	 *
	 * @param db
	 *            Git repository
	 * @param noteMerger
	 *            note merger for merging conflicting changes on a note
	 * @param nonNotesMergeStrategy
	 *            merge strategy for merging non-note entries
	 */
	public NoteMapMerger(Repository db, NoteMerger noteMerger,
			MergeStrategy nonNotesMergeStrategy) {
		this.db = db;
		this.reader = db.newObjectReader();
		this.inserter = db.newObjectInserter();
		this.noteMerger = noteMerger;
		this.nonNotesMergeStrategy = nonNotesMergeStrategy;
		this.objectIdPrefix = new MutableObjectId();
	}

	/**
	 * Constructs a NoteMapMerger with
	 * {@link DefaultNoteMerger} as the merger for notes
	 * and the {@link MergeStrategy#RESOLVE} as the
	 * strategy for resolving conflicts on non-notes
	 *
	 * @param db
	 *            Git repository
	 */
	public NoteMapMerger(Repository db) {
		this(db, new DefaultNoteMerger(), MergeStrategy.RESOLVE);
	}

	/**
	 * Performs the merge.
	 *
	 * @param base
	 *            base version of the note tree
	 * @param ours
	 *            ours version of the note tree
	 * @param theirs
	 *            theirs version of the note tree
	 * @return merge result as a new NoteMap
	 * @throws IOException
	 */
	public NoteMap merge(NoteMap base, NoteMap ours, NoteMap theirs)
			throws IOException {
		try {
			org.eclipse.jgit.notes.InMemoryNoteBucket mergedBucket = merge(0, base.getRoot(),
					ours.getRoot(), theirs.getRoot());
			inserter.flush();
			return NoteMap.newMap(mergedBucket, reader);
		} finally {
			reader.close();
			inserter.close();
		}
	}

	/**
	 * This method is called only when it is known that there is some difference
	 * between base, ours and theirs.
	 *
	 * @param treeDepth
	 * @param base
	 * @param ours
	 * @param theirs
	 * @return merge result as an InMemoryBucket
	 * @throws IOException
	 */
	private org.eclipse.jgit.notes.InMemoryNoteBucket merge(int treeDepth, org.eclipse.jgit.notes.InMemoryNoteBucket base,
	                                                        org.eclipse.jgit.notes.InMemoryNoteBucket ours, org.eclipse.jgit.notes.InMemoryNoteBucket theirs)
			throws IOException {
		org.eclipse.jgit.notes.InMemoryNoteBucket result;

		if (base instanceof FanoutBucket || ours instanceof FanoutBucket
				|| theirs instanceof FanoutBucket) {
			result = mergeFanoutBucket(treeDepth, asFanout(base),
					asFanout(ours), asFanout(theirs));

		} else {
			result = mergeLeafBucket(treeDepth, (org.eclipse.jgit.notes.LeafBucket) base,
					(org.eclipse.jgit.notes.LeafBucket) ours, (org.eclipse.jgit.notes.LeafBucket) theirs);
		}

		result.nonNotes = mergeNonNotes(nonNotes(base), nonNotes(ours),
				nonNotes(theirs));
		return result;
	}

	private FanoutBucket asFanout(org.eclipse.jgit.notes.InMemoryNoteBucket bucket) {
		if (bucket == null)
			return EMPTY_FANOUT;
		if (bucket instanceof FanoutBucket)
			return (FanoutBucket) bucket;
		return ((org.eclipse.jgit.notes.LeafBucket) bucket).split();
	}

	private static org.eclipse.jgit.notes.NonNoteEntry nonNotes(org.eclipse.jgit.notes.InMemoryNoteBucket b) {
		return b == null ? null : b.nonNotes;
	}

	private org.eclipse.jgit.notes.InMemoryNoteBucket mergeFanoutBucket(int treeDepth,
	                                                                    FanoutBucket base,
	                                                                    FanoutBucket ours, FanoutBucket theirs) throws IOException {
		FanoutBucket result = new FanoutBucket(treeDepth * 2);
		// walking through entries of base, ours, theirs
		for (int i = 0; i < 256; i++) {
			NoteBucket b = base.getBucket(i);
			NoteBucket o = ours.getBucket(i);
			NoteBucket t = theirs.getBucket(i);

			if (equals(o, t))
				addIfNotNull(result, i, o);

			else if (equals(b, o))
				addIfNotNull(result, i, t);

			else if (equals(b, t))
				addIfNotNull(result, i, o);

			else {
				objectIdPrefix.setByte(treeDepth, i);
				org.eclipse.jgit.notes.InMemoryNoteBucket mergedBucket = merge(treeDepth + 1,
						FanoutBucket.loadIfLazy(b, objectIdPrefix, reader),
						FanoutBucket.loadIfLazy(o, objectIdPrefix, reader),
						FanoutBucket.loadIfLazy(t, objectIdPrefix, reader));
				result.setBucket(i, mergedBucket);
			}
		}
		return result.contractIfTooSmall(objectIdPrefix, reader);
	}

	private static boolean equals(NoteBucket a, NoteBucket b) {
		if (a == null && b == null)
			return true;
		return a != null && b != null && a.getTreeId().equals(b.getTreeId());
	}

	private void addIfNotNull(FanoutBucket b, int cell, NoteBucket child)
			throws IOException {
		if (child == null)
			return;
		if (child instanceof org.eclipse.jgit.notes.InMemoryNoteBucket)
			b.setBucket(cell, ((org.eclipse.jgit.notes.InMemoryNoteBucket) child).writeTree(inserter));
		else
			b.setBucket(cell, child.getTreeId());
	}

	private org.eclipse.jgit.notes.InMemoryNoteBucket mergeLeafBucket(int treeDepth, org.eclipse.jgit.notes.LeafBucket bb,
	                                                                  org.eclipse.jgit.notes.LeafBucket ob, org.eclipse.jgit.notes.LeafBucket tb) throws MissingObjectException,
			IOException {
		bb = notNullOrEmpty(bb);
		ob = notNullOrEmpty(ob);
		tb = notNullOrEmpty(tb);

		org.eclipse.jgit.notes.InMemoryNoteBucket result = new org.eclipse.jgit.notes.LeafBucket(treeDepth * 2);
		int bi = 0, oi = 0, ti = 0;
		while (bi < bb.size() || oi < ob.size() || ti < tb.size()) {
			Note b = get(bb, bi), o = get(ob, oi), t = get(tb, ti);
			Note min = min(b, o, t);

			b = sameNoteOrNull(min, b);
			o = sameNoteOrNull(min, o);
			t = sameNoteOrNull(min, t);

			if (sameContent(o, t))
				result = addIfNotNull(result, o);

			else if (sameContent(b, o))
				result = addIfNotNull(result, t);

			else if (sameContent(b, t))
				result = addIfNotNull(result, o);

			else
				result = addIfNotNull(result,
						noteMerger.merge(b, o, t, reader, inserter));

			if (b != null)
				bi++;
			if (o != null)
				oi++;
			if (t != null)
				ti++;
		}
		return result;
	}

	private static org.eclipse.jgit.notes.LeafBucket notNullOrEmpty(org.eclipse.jgit.notes.LeafBucket b) {
		return b != null ? b : EMPTY_LEAF;
	}

	private static Note get(org.eclipse.jgit.notes.LeafBucket b, int i) {
		return i < b.size() ? b.get(i) : null;
	}

	private static Note min(Note b, Note o, Note t) {
		Note min = b;
		if (min == null || (o != null && o.compareTo(min) < 0))
			min = o;
		if (min == null || (t != null && t.compareTo(min) < 0))
			min = t;
		return min;
	}

	private static Note sameNoteOrNull(Note min, Note other) {
		return sameNote(min, other) ? other : null;
	}

	private static boolean sameNote(Note a, Note b) {
		if (a == null && b == null)
			return true;
		return a != null && b != null && AnyObjectId.isEqual(a, b);
	}

	private static boolean sameContent(Note a, Note b) {
		if (a == null && b == null)
			return true;
		return a != null && b != null
				&& AnyObjectId.isEqual(a.getData(), b.getData());
	}

	private static org.eclipse.jgit.notes.InMemoryNoteBucket addIfNotNull(org.eclipse.jgit.notes.InMemoryNoteBucket result,
	                                                                      Note note) {
		if (note != null) {
			return result.append(note);
		}
		return result;
	}

	private org.eclipse.jgit.notes.NonNoteEntry mergeNonNotes(org.eclipse.jgit.notes.NonNoteEntry baseList,
	                                                          org.eclipse.jgit.notes.NonNoteEntry oursList, org.eclipse.jgit.notes.NonNoteEntry theirsList) throws IOException {
		if (baseList == null && oursList == null && theirsList == null)
			return null;

		ObjectId baseId = write(baseList);
		ObjectId oursId = write(oursList);
		ObjectId theirsId = write(theirsList);
		inserter.flush();

		Merger m = nonNotesMergeStrategy.newMerger(db, true);
		if (m instanceof ThreeWayMerger)
			((ThreeWayMerger) m).setBase(baseId);
		if (!m.merge(oursId, theirsId))
			throw new NotesMergeConflictException(baseList, oursList,
					theirsList);
		ObjectId resultTreeId = m.getResultTreeId();
		AbbreviatedObjectId none = AbbreviatedObjectId.fromString(""); //$NON-NLS-1$
		return NoteParser.parse(none, resultTreeId, reader).nonNotes;
	}

	private ObjectId write(org.eclipse.jgit.notes.NonNoteEntry list)
			throws IOException {
		org.eclipse.jgit.notes.LeafBucket b = new org.eclipse.jgit.notes.LeafBucket(0);
		b.nonNotes = list;
		return b.writeTree(inserter);
	}
}
