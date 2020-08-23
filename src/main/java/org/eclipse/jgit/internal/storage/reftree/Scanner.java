/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftree;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.Paths;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.RefList;

import java.io.IOException;

import static org.eclipse.jgit.lib.Constants.*;
import static org.eclipse.jgit.lib.FileMode.TYPE_TREE;
import static org.eclipse.jgit.lib.FileMode.*;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.eclipse.jgit.lib.RefDatabase.MAX_SYMBOLIC_REF_DEPTH;

/** A tree parser that extracts references from a {@link RefTree}. */
class Scanner {
	private static final int MAX_SYMLINK_BYTES = 10 << 10;
	private static final byte[] BINARY_R_REFS = encode(R_REFS);
	private static final byte[] REFS_DOT_DOT = encode("refs/.."); //$NON-NLS-1$

	static class Result {
		final ObjectId refTreeId;
		final RefList<Ref> all;
		final RefList<Ref> sym;

		Result(ObjectId id, RefList<Ref> all, RefList<Ref> sym) {
			this.refTreeId = id;
			this.all = all;
			this.sym = sym;
		}
	}

	/**
	 * Scan a {@link RefTree} and parse entries into {@link Ref} instances.
	 *
	 * @param repo
	 *            source repository containing the commit and tree objects that
	 *            make up the RefTree.
	 * @param src
	 *            bootstrap reference such as {@code refs/txn/committed} to read
	 *            the reference tree tip from. The current ObjectId will be
	 *            included in {@link Result#refTreeId}.
	 * @param prefix
	 *            if non-empty a reference prefix to scan only a subdirectory.
	 *            For example {@code prefix = "refs/heads/"} will limit the scan
	 *            to only the {@code "heads"} directory of the RefTree, avoiding
	 *            other directories like {@code "tags"}. Empty string reads all
	 *            entries in the RefTree.
	 * @param recursive
	 *            if true recurse into subdirectories of the reference tree;
	 *            false to read only one level. Callers may use false during an
	 *            implementation of {@code exactRef(String)} where only one
	 *            reference is needed out of a specific subtree.
	 * @return sorted list of references after parsing.
	 * @throws IOException
	 *             tree cannot be accessed from the repository.
	 */
	static Result scanRefTree(Repository repo, @Nullable Ref src, String prefix,
			boolean recursive) throws IOException {
		RefList.Builder<Ref> all = new RefList.Builder<>();
		RefList.Builder<Ref> sym = new RefList.Builder<>();

		ObjectId srcId;
		if (src != null && src.getObjectId() != null) {
			try (ObjectReader reader = repo.newObjectReader()) {
				srcId = src.getObjectId();
				scan(reader, srcId, prefix, recursive, all, sym);
			}
		} else {
			srcId = ObjectId.zeroId();
		}

		RefList<Ref> aList = all.toRefList();
		for (int idx = 0; idx < sym.size();) {
			Ref s = sym.get(idx);
			Ref r = resolve(s, 0, aList);
			if (r != null) {
				sym.set(idx++, r);
			} else {
				// Remove broken symbolic reference, they don't exist.
				sym.remove(idx);
				int rm = aList.find(s.getName());
				if (0 <= rm) {
					aList = aList.remove(rm);
				}
			}
		}
		return new Result(srcId, aList, sym.toRefList());
	}

	private static void scan(ObjectReader reader, AnyObjectId srcId,
			String prefix, boolean recursive,
			RefList.Builder<Ref> all, RefList.Builder<Ref> sym)
					throws IncorrectObjectTypeException, IOException {
		CanonicalTreeParser p = createParserAtPath(reader, srcId, prefix);
		if (p == null) {
			return;
		}

		while (!p.eof()) {
			int mode = p.getEntryRawMode();
			if (mode == TYPE_TREE) {
				if (recursive) {
					p = p.createSubtreeIterator(reader);
				} else {
					p = p.next();
				}
				continue;
			}

			if (!curElementHasPeelSuffix(p)) {
				Ref r = toRef(reader, mode, p);
				if (r != null) {
					all.add(r);
					if (r.isSymbolic()) {
						sym.add(r);
					}
				}
			} else if (mode == TYPE_GITLINK) {
				peel(all, p);
			}
			p = p.next();
		}
	}

	private static CanonicalTreeParser createParserAtPath(ObjectReader reader,
			AnyObjectId srcId, String prefix) throws IOException {
		ObjectId root = toTree(reader, srcId);
		if (prefix.isEmpty()) {
			return new CanonicalTreeParser(BINARY_R_REFS, reader, root);
		}

		String dir = RefTree.refPath(Paths.stripTrailingSeparator(prefix));
		TreeWalk tw = TreeWalk.forPath(reader, dir, root);
		if (tw == null || !tw.isSubtree()) {
			return null;
		}

		ObjectId id = tw.getObjectId(0);
		return new CanonicalTreeParser(encode(prefix), reader, id);
	}

	private static Ref resolve(Ref ref, int depth, RefList<Ref> refs)
			throws IOException {
		if (!ref.isSymbolic()) {
			return ref;
		} else if (MAX_SYMBOLIC_REF_DEPTH <= depth) {
			return null;
		}

		Ref r = refs.get(ref.getTarget().getName());
		if (r == null) {
			return ref;
		}

		Ref dst = resolve(r, depth + 1, refs);
		if (dst == null) {
			return null;
		}
		return new SymbolicRef(ref.getName(), dst);
	}

	private static RevTree toTree(ObjectReader reader, AnyObjectId id)
			throws IOException {
		try (RevWalk rw = new RevWalk(reader)) {
			return rw.parseTree(id);
		}
	}

	private static boolean curElementHasPeelSuffix(AbstractTreeIterator itr) {
		int n = itr.getEntryPathLength();
		byte[] c = itr.getEntryPathBuffer();
		return n > 2 && c[n - 2] == ' ' && c[n - 1] == '^';
	}

	private static void peel(RefList.Builder<Ref> all, CanonicalTreeParser p) {
		String name = refName(p, true);
		for (int idx = all.size() - 1; 0 <= idx; idx--) {
			Ref r = all.get(idx);
			int cmp = r.getName().compareTo(name);
			if (cmp == 0) {
				all.set(idx, new ObjectIdRef.PeeledTag(PACKED, r.getName(),
						r.getObjectId(), p.getEntryObjectId()));
				break;
			} else if (cmp < 0) {
				// Stray peeled name without matching base name; skip entry.
				break;
			}
		}
	}

	private static Ref toRef(ObjectReader reader, int mode,
			CanonicalTreeParser p) throws IOException {
		if (mode == TYPE_GITLINK) {
			String name = refName(p, false);
			ObjectId id = p.getEntryObjectId();
			return new ObjectIdRef.PeeledNonTag(PACKED, name, id);

		} else if (mode == TYPE_SYMLINK) {
			ObjectId id = p.getEntryObjectId();
			byte[] bin = reader.open(id, OBJ_BLOB)
					.getCachedBytes(MAX_SYMLINK_BYTES);
			String dst = RawParseUtils.decode(bin);
			Ref trg = new ObjectIdRef.Unpeeled(NEW, dst, null);
			String name = refName(p, false);
			return new SymbolicRef(name, trg);
		}
		return null;
	}

	private static String refName(CanonicalTreeParser p, boolean peel) {
		byte[] buf = p.getEntryPathBuffer();
		int len = p.getEntryPathLength();
		if (peel) {
			len -= 2;
		}
		int ptr = 0;
		if (RawParseUtils.match(buf, ptr, REFS_DOT_DOT) > 0) {
			ptr = 7;
		}
		return RawParseUtils.decode(buf, ptr, len);
	}

	private Scanner() {
	}
}
