/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.events.WorkingTreeModifiedEvent;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.merge.MergeMessageFormatter;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.FileTreeIterator;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;

/**
 * A class used to execute a {@code cherry-pick} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-cherry-pick.html"
 *      >Git documentation about cherry-pick</a>
 */
public class CherryPickCommand extends GitCommand<CherryPickResult> {
	private String reflogPrefix = "cherry-pick:"; //$NON-NLS-1$

	private List<Ref> commits = new LinkedList<>();

	private String ourCommitName = null;

	private MergeStrategy strategy = MergeStrategy.RECURSIVE;

	private Integer mainlineParentNumber;

	private boolean noCommit = false;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	/**
	 * Constructor for CherryPickCommand
	 *
	 * @param repo
	 *            the {@link Repository}
	 */
	protected CherryPickCommand(Repository repo) {
		super(repo);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Executes the {@code Cherry-Pick} command with all the options and
	 * parameters collected by the setter methods (e.g. {@link #include(Ref)} of
	 * this class. Each instance of this class should only be used for one
	 * invocation of the command. Don't call this method twice on an instance.
	 */
	@Override
	public CherryPickResult call() throws GitAPIException, NoMessageException,
			UnmergedPathsException, ConcurrentRefUpdateException,
			WrongRepositoryStateException, NoHeadException {
		RevCommit newHead = null;
		List<Ref> cherryPickedRefs = new LinkedList<>();
		checkCallable();

		try (RevWalk revWalk = new RevWalk(repo)) {

			// get the head commit
			Ref headRef = repo.exactRef(Constants.HEAD);
			if (headRef == null) {
				throw new NoHeadException(
						JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);
			}

			newHead = revWalk.parseCommit(headRef.getObjectId());

			// loop through all refs to be cherry-picked
			for (Ref src : commits) {
				// get the commit to be cherry-picked
				// handle annotated tags
				ObjectId srcObjectId = src.getPeeledObjectId();
				if (srcObjectId == null) {
					srcObjectId = src.getObjectId();
				}
				RevCommit srcCommit = revWalk.parseCommit(srcObjectId);

				// get the parent of the commit to cherry-pick
				final RevCommit srcParent = getParentCommit(srcCommit, revWalk);

				String ourName = calculateOurName(headRef);
				String cherryPickName = srcCommit.getId().abbreviate(7).name()
						+ " " + srcCommit.getShortMessage(); //$NON-NLS-1$

				ResolveMerger merger = (ResolveMerger) strategy.newMerger(repo);
				merger.setWorkingTreeIterator(new FileTreeIterator(repo));
				merger.setBase(srcParent.getTree());
				merger.setCommitNames(new String[] { "BASE", ourName, //$NON-NLS-1$
						cherryPickName });
				if (merger.merge(newHead, srcCommit)) {
					if (!merger.getModifiedFiles().isEmpty()) {
						repo.fireEvent(new WorkingTreeModifiedEvent(
								merger.getModifiedFiles(), null));
					}
					if (AnyObjectId.isEqual(newHead.getTree().getId(),
							merger.getResultTreeId())) {
						continue;
					}
					DirCacheCheckout dco = new DirCacheCheckout(repo,
							newHead.getTree(), repo.lockDirCache(),
							merger.getResultTreeId());
					dco.setFailOnConflict(true);
					dco.setProgressMonitor(monitor);
					dco.checkout();
					if (!noCommit) {
						try (Git git = new Git(getRepository())) {
							newHead = git.commit()
									.setMessage(srcCommit.getFullMessage())
									.setReflogComment(reflogPrefix + " " //$NON-NLS-1$
											+ srcCommit.getShortMessage())
									.setAuthor(srcCommit.getAuthorIdent())
									.setNoVerify(true).call();
						}
					}
					cherryPickedRefs.add(src);
				} else {
					if (merger.failed()) {
						return new CherryPickResult(merger.getFailingPaths());
					}

					// there are merge conflicts

					String message = new MergeMessageFormatter()
							.formatWithConflicts(srcCommit.getFullMessage(),
									merger.getUnmergedPaths());

					if (!noCommit) {
						repo.writeCherryPickHead(srcCommit.getId());
					}
					repo.writeMergeCommitMsg(message);

					repo.fireEvent(new WorkingTreeModifiedEvent(
							merger.getModifiedFiles(), null));

					return CherryPickResult.CONFLICT;
				}
			}
		} catch (IOException e) {
			throw new JGitInternalException(
					MessageFormat.format(
							JGitText.get().exceptionCaughtDuringExecutionOfCherryPickCommand,
							e), e);
		}
		return new CherryPickResult(newHead, cherryPickedRefs);
	}

	private RevCommit getParentCommit(RevCommit srcCommit, RevWalk revWalk)
			throws MultipleParentsNotAllowedException, MissingObjectException,
			IOException {
		final RevCommit srcParent;
		if (mainlineParentNumber == null) {
			if (srcCommit.getParentCount() != 1)
				throw new MultipleParentsNotAllowedException(
						MessageFormat.format(
								JGitText.get().canOnlyCherryPickCommitsWithOneParent,
								srcCommit.name(),
								Integer.valueOf(srcCommit.getParentCount())));
			srcParent = srcCommit.getParent(0);
		} else {
			if (mainlineParentNumber.intValue() > srcCommit.getParentCount()) {
				throw new JGitInternalException(MessageFormat.format(
						JGitText.get().commitDoesNotHaveGivenParent, srcCommit,
						mainlineParentNumber));
			}
			srcParent = srcCommit
					.getParent(mainlineParentNumber.intValue() - 1);
		}

		revWalk.parseHeaders(srcParent);
		return srcParent;
	}

	/**
	 * Include a reference to a commit
	 *
	 * @param commit
	 *            a reference to a commit which is cherry-picked to the current
	 *            head
	 * @return {@code this}
	 */
	public org.eclipse.jgit.api.CherryPickCommand include(Ref commit) {
		checkCallable();
		commits.add(commit);
		return this;
	}

	/**
	 * Include a commit
	 *
	 * @param commit
	 *            the Id of a commit which is cherry-picked to the current head
	 * @return {@code this}
	 */
	public org.eclipse.jgit.api.CherryPickCommand include(AnyObjectId commit) {
		return include(commit.getName(), commit);
	}

	/**
	 * Include a commit
	 *
	 * @param name
	 *            a name given to the commit
	 * @param commit
	 *            the Id of a commit which is cherry-picked to the current head
	 * @return {@code this}
	 */
	public org.eclipse.jgit.api.CherryPickCommand include(String name, AnyObjectId commit) {
		return include(new ObjectIdRef.Unpeeled(Storage.LOOSE, name,
				commit.copy()));
	}

	/**
	 * Set the name that should be used in the "OURS" place for conflict markers
	 *
	 * @param ourCommitName
	 *            the name that should be used in the "OURS" place for conflict
	 *            markers
	 * @return {@code this}
	 */
	public org.eclipse.jgit.api.CherryPickCommand setOurCommitName(String ourCommitName) {
		this.ourCommitName = ourCommitName;
		return this;
	}

	/**
	 * Set the prefix to use in the reflog.
	 * <p>
	 * This is primarily needed for implementing rebase in terms of
	 * cherry-picking
	 *
	 * @param prefix
	 *            including ":"
	 * @return {@code this}
	 * @since 3.1
	 */
	public org.eclipse.jgit.api.CherryPickCommand setReflogPrefix(String prefix) {
		this.reflogPrefix = prefix;
		return this;
	}

	/**
	 * Set the {@code MergeStrategy}
	 *
	 * @param strategy
	 *            The merge strategy to use during this Cherry-pick.
	 * @return {@code this}
	 * @since 3.4
	 */
	public org.eclipse.jgit.api.CherryPickCommand setStrategy(MergeStrategy strategy) {
		this.strategy = strategy;
		return this;
	}

	/**
	 * Set the (1-based) parent number to diff against
	 *
	 * @param mainlineParentNumber
	 *            the (1-based) parent number to diff against. This allows
	 *            cherry-picking of merges.
	 * @return {@code this}
	 * @since 3.4
	 */
	public org.eclipse.jgit.api.CherryPickCommand setMainlineParentNumber(int mainlineParentNumber) {
		this.mainlineParentNumber = Integer.valueOf(mainlineParentNumber);
		return this;
	}

	/**
	 * Allows cherry-picking changes without committing them.
	 * <p>
	 * NOTE: The behavior of cherry-pick is undefined if you pick multiple
	 * commits or if HEAD does not match the index state before cherry-picking.
	 *
	 * @param noCommit
	 *            true to cherry-pick without committing, false to commit after
	 *            each pick (default)
	 * @return {@code this}
	 * @since 3.5
	 */
	public org.eclipse.jgit.api.CherryPickCommand setNoCommit(boolean noCommit) {
		this.noCommit = noCommit;
		return this;
	}

	/**
	 * The progress monitor associated with the cherry-pick operation. By
	 * default, this is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 * @param monitor
	 *            a {@link ProgressMonitor}
	 * @return {@code this}
	 * @since 4.11
	 */
	public org.eclipse.jgit.api.CherryPickCommand setProgressMonitor(ProgressMonitor monitor) {
		if (monitor == null) {
			monitor = NullProgressMonitor.INSTANCE;
		}
		this.monitor = monitor;
		return this;
	}

	private String calculateOurName(Ref headRef) {
		if (ourCommitName != null)
			return ourCommitName;

		String targetRefName = headRef.getTarget().getName();
		String headName = Repository.shortenRefName(targetRefName);
		return headName;
	}

	/** {@inheritDoc} */
	@SuppressWarnings("nls")
	@Override
	public String toString() {
		return "CherryPickCommand [repo=" + repo + ",\ncommits=" + commits
				+ ",\nmainlineParentNumber=" + mainlineParentNumber
				+ ", noCommit=" + noCommit + ", ourCommitName=" + ourCommitName
				+ ", reflogPrefix=" + reflogPrefix + ", strategy=" + strategy
				+ "]";
	}

}
