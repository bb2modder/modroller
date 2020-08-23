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
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.RefList;
import org.eclipse.jgit.util.RefMap;

import java.io.IOException;
import java.util.*;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Ref.Storage.LOOSE;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;

/**
 * Reference database backed by a
 * {@link org.eclipse.jgit.internal.storage.reftree.RefTree}.
 * <p>
 * The storage for RefTreeDatabase has two parts. The main part is a native Git
 * tree object stored under the {@code refs/txn} namespace. To avoid cycles,
 * references to {@code refs/txn} are not stored in that tree object, but
 * instead in a "bootstrap" layer, which is a separate
 * {@link RefDatabase} such as
 * {@link org.eclipse.jgit.internal.storage.file.RefDirectory} using local
 * reference files inside of {@code $GIT_DIR/refs}.
 */
public class RefTreeDatabase extends RefDatabase {
	private final Repository repo;
	private final RefDatabase bootstrap;
	private final String txnCommitted;

	@Nullable
	private final String txnNamespace;
	private volatile org.eclipse.jgit.internal.storage.reftree.Scanner.Result refs;

	/**
	 * Create a RefTreeDb for a repository.
	 *
	 * @param repo
	 *            the repository using references in this database.
	 * @param bootstrap
	 *            bootstrap reference database storing the references that
	 *            anchor the
	 *            {@link org.eclipse.jgit.internal.storage.reftree.RefTree}.
	 */
	public RefTreeDatabase(Repository repo, RefDatabase bootstrap) {
		Config cfg = repo.getConfig();
		String committed = cfg.getString("reftree", null, "committedRef"); //$NON-NLS-1$ //$NON-NLS-2$
		if (committed == null || committed.isEmpty()) {
			committed = "refs/txn/committed"; //$NON-NLS-1$
		}

		this.repo = repo;
		this.bootstrap = bootstrap;
		this.txnNamespace = initNamespace(committed);
		this.txnCommitted = committed;
	}

	/**
	 * Create a RefTreeDb for a repository.
	 *
	 * @param repo
	 *            the repository using references in this database.
	 * @param bootstrap
	 *            bootstrap reference database storing the references that
	 *            anchor the
	 *            {@link org.eclipse.jgit.internal.storage.reftree.RefTree}.
	 * @param txnCommitted
	 *            name of the bootstrap reference holding the committed RefTree.
	 */
	public RefTreeDatabase(Repository repo, RefDatabase bootstrap,
			String txnCommitted) {
		this.repo = repo;
		this.bootstrap = bootstrap;
		this.txnNamespace = initNamespace(txnCommitted);
		this.txnCommitted = txnCommitted;
	}

	private static String initNamespace(String committed) {
		int s = committed.lastIndexOf('/');
		if (s < 0) {
			return null;
		}
		return committed.substring(0, s + 1); // Keep trailing '/'.
	}

	Repository getRepository() {
		return repo;
	}

	/**
	 * Get the bootstrap reference database
	 *
	 * @return the bootstrap reference database, which must be used to access
	 *         {@link #getTxnCommitted()}, {@link #getTxnNamespace()}.
	 */
	public RefDatabase getBootstrap() {
		return bootstrap;
	}

	/**
	 * Get name of bootstrap reference anchoring committed RefTree.
	 *
	 * @return name of bootstrap reference anchoring committed RefTree.
	 */
	public String getTxnCommitted() {
		return txnCommitted;
	}

	/**
	 * Get namespace used by bootstrap layer.
	 *
	 * @return namespace used by bootstrap layer, e.g. {@code refs/txn/}. Always
	 *         ends in {@code '/'}.
	 */
	@Nullable
	public String getTxnNamespace() {
		return txnNamespace;
	}

	/** {@inheritDoc} */
	@Override
	public void create() throws IOException {
		bootstrap.create();
	}

	/** {@inheritDoc} */
	@Override
	public boolean performsAtomicTransactions() {
		return true;
	}

	/** {@inheritDoc} */
	@Override
	public void refresh() {
		bootstrap.refresh();
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		refs = null;
		bootstrap.close();
	}

	/** {@inheritDoc} */
	@Override
	public Ref exactRef(String name) throws IOException {
		if (!repo.isBare() && name.indexOf('/') < 0 && !HEAD.equals(name)) {
			// Pass through names like MERGE_HEAD, ORIG_HEAD, FETCH_HEAD.
			return bootstrap.exactRef(name);
		} else if (conflictsWithBootstrap(name)) {
			return null;
		}

		boolean partial = false;
		Ref src = bootstrap.exactRef(txnCommitted);
		org.eclipse.jgit.internal.storage.reftree.Scanner.Result c = refs;
		if (c == null || !c.refTreeId.equals(idOf(src))) {
			c = org.eclipse.jgit.internal.storage.reftree.Scanner.scanRefTree(repo, src, prefixOf(name), false);
			partial = true;
		}

		Ref r = c.all.get(name);
		if (r != null && r.isSymbolic()) {
			r = c.sym.get(name);
			if (partial && r.getObjectId() == null) {
				// Attempting exactRef("HEAD") with partial scan will leave
				// an unresolved symref as its target e.g. refs/heads/master
				// was not read by the partial scan. Scan everything instead.
				return getRefs(ALL).get(name);
			}
		}
		return r;
	}

	private static String prefixOf(String name) {
		int s = name.lastIndexOf('/');
		if (s >= 0) {
			return name.substring(0, s);
		}
		return ""; //$NON-NLS-1$
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Ref> getRefs(String prefix) throws IOException {
		if (!prefix.isEmpty() && prefix.charAt(prefix.length() - 1) != '/') {
			return new HashMap<>(0);
		}

		Ref src = bootstrap.exactRef(txnCommitted);
		org.eclipse.jgit.internal.storage.reftree.Scanner.Result c = refs;
		if (c == null || !c.refTreeId.equals(idOf(src))) {
			c = org.eclipse.jgit.internal.storage.reftree.Scanner.scanRefTree(repo, src, prefix, true);
			if (prefix.isEmpty()) {
				refs = c;
			}
		}
		return new RefMap(prefix, RefList.<Ref> emptyList(), c.all, c.sym);
	}

	private static ObjectId idOf(@Nullable Ref src) {
		return src != null && src.getObjectId() != null
				? src.getObjectId()
				: ObjectId.zeroId();
	}

	/** {@inheritDoc} */
	@Override
	public List<Ref> getAdditionalRefs() throws IOException {
		Collection<Ref> txnRefs;
		if (txnNamespace != null) {
			txnRefs = bootstrap.getRefsByPrefix(txnNamespace);
		} else {
			Ref r = bootstrap.exactRef(txnCommitted);
			if (r != null && r.getObjectId() != null) {
				txnRefs = Collections.singleton(r);
			} else {
				txnRefs = Collections.emptyList();
			}
		}

		List<Ref> otherRefs = bootstrap.getAdditionalRefs();
		List<Ref> all = new ArrayList<>(txnRefs.size() + otherRefs.size());
		all.addAll(txnRefs);
		all.addAll(otherRefs);
		return all;
	}

	/** {@inheritDoc} */
	@Override
	public Ref peel(Ref ref) throws IOException {
		Ref i = ref.getLeaf();
		ObjectId id = i.getObjectId();
		if (i.isPeeled() || id == null) {
			return ref;
		}
		try (RevWalk rw = new RevWalk(repo)) {
			RevObject obj = rw.parseAny(id);
			if (obj instanceof RevTag) {
				ObjectId p = rw.peel(obj).copy();
				i = new ObjectIdRef.PeeledTag(PACKED, i.getName(), id, p);
			} else {
				i = new ObjectIdRef.PeeledNonTag(PACKED, i.getName(), id);
			}
		}
		return recreate(ref, i);
	}

	private static Ref recreate(Ref old, Ref leaf) {
		if (old.isSymbolic()) {
			Ref dst = recreate(old.getTarget(), leaf);
			return new SymbolicRef(old.getName(), dst);
		}
		return leaf;
	}

	/** {@inheritDoc} */
	@Override
	public boolean isNameConflicting(String name) throws IOException {
		return conflictsWithBootstrap(name)
				|| !getConflictingNames(name).isEmpty();
	}

	/** {@inheritDoc} */
	@Override
	public BatchRefUpdate newBatchUpdate() {
		return new org.eclipse.jgit.internal.storage.reftree.RefTreeBatch(this);
	}

	/** {@inheritDoc} */
	@Override
	public RefUpdate newUpdate(String name, boolean detach) throws IOException {
		if (!repo.isBare() && name.indexOf('/') < 0 && !HEAD.equals(name)) {
			return bootstrap.newUpdate(name, detach);
		}
		if (conflictsWithBootstrap(name)) {
			return new org.eclipse.jgit.internal.storage.reftree.AlwaysFailUpdate(this, name);
		}

		Ref r = exactRef(name);
		if (r == null) {
			r = new ObjectIdRef.Unpeeled(Storage.NEW, name, null);
		}

		boolean detaching = detach && r.isSymbolic();
		if (detaching) {
			r = new ObjectIdRef.Unpeeled(LOOSE, name, r.getObjectId());
		}

		org.eclipse.jgit.internal.storage.reftree.RefTreeUpdate u = new org.eclipse.jgit.internal.storage.reftree.RefTreeUpdate(this, r);
		if (detaching) {
			u.setDetachingSymbolicRef();
		}
		return u;
	}

	/** {@inheritDoc} */
	@Override
	public RefRename newRename(String fromName, String toName)
			throws IOException {
		RefUpdate from = newUpdate(fromName, true);
		RefUpdate to = newUpdate(toName, true);
		return new org.eclipse.jgit.internal.storage.reftree.RefTreeRename(this, from, to);
	}

	boolean conflictsWithBootstrap(String name) {
		if (txnNamespace != null && name.startsWith(txnNamespace)) {
			return true;
		} else if (txnCommitted.equals(name)) {
			return true;
		}

		if (name.indexOf('/') < 0 && !HEAD.equals(name)) {
			return true;
		}

		if (name.length() > txnCommitted.length()
				&& name.charAt(txnCommitted.length()) == '/'
				&& name.startsWith(txnCommitted)) {
			return true;
		}
		return false;
	}
}
