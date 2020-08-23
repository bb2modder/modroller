/*
 * Copyright (C) 2010, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2010-2014, Stefan Lay <stefan.lay@sap.com>
 * Copyright (C) 2016, Laurent Delaigue <laurent.delaigue@obeo.fr> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.events.WorkingTreeModifiedEvent;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.lib.Config.ConfigEnum;
import org.eclipse.jgit.lib.Ref.Storage;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.merge.*;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.StringUtils;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

/**
 * A class used to execute a {@code Merge} command. It has setters for all
 * supported options and arguments of this command and a {@link #call()} method
 * to finally execute the command. Each instance of this class should only be
 * used for one invocation of the command (means: one call to {@link #call()})
 *
 * @see <a href="http://www.kernel.org/pub/software/scm/git/docs/git-merge.html"
 *      >Git documentation about Merge</a>
 */
public class MergeCommand extends GitCommand<MergeResult> {

	private MergeStrategy mergeStrategy = MergeStrategy.RECURSIVE;

	private List<Ref> commits = new LinkedList<>();

	private Boolean squash;

	private FastForwardMode fastForwardMode;

	private String message;

	private boolean insertChangeId;

	private ProgressMonitor monitor = NullProgressMonitor.INSTANCE;

	/**
	 * The modes available for fast forward merges corresponding to the
	 * <code>--ff</code>, <code>--no-ff</code> and <code>--ff-only</code>
	 * options under <code>branch.&lt;name&gt;.mergeoptions</code>.
	 */
	public enum FastForwardMode implements ConfigEnum {
		/**
		 * Corresponds to the default --ff option (for a fast forward update the
		 * branch pointer only).
		 */
		FF,
		/**
		 * Corresponds to the --no-ff option (create a merge commit even for a
		 * fast forward).
		 */
		NO_FF,
		/**
		 * Corresponds to the --ff-only option (abort unless the merge is a fast
		 * forward).
		 */
		FF_ONLY;

		@Override
		public String toConfigValue() {
			return "--" + name().toLowerCase(Locale.ROOT).replace('_', '-'); //$NON-NLS-1$
		}

		@Override
		public boolean matchConfigValue(String in) {
			if (StringUtils.isEmptyOrNull(in))
				return false;
			if (!in.startsWith("--")) //$NON-NLS-1$
				return false;
			return name().equalsIgnoreCase(in.substring(2).replace('-', '_'));
		}

		/**
		 * The modes available for fast forward merges corresponding to the
		 * options under <code>merge.ff</code>.
		 */
		public enum Merge {
			/**
			 * {@link FastForwardMode#FF}.
			 */
			TRUE,
			/**
			 * {@link FastForwardMode#NO_FF}.
			 */
			FALSE,
			/**
			 * {@link FastForwardMode#FF_ONLY}.
			 */
			ONLY;

			/**
			 * Map from <code>FastForwardMode</code> to
			 * <code>FastForwardMode.Merge</code>.
			 *
			 * @param ffMode
			 *            the <code>FastForwardMode</code> value to be mapped
			 * @return the mapped <code>FastForwardMode.Merge</code> value
			 */
			public static Merge valueOf(FastForwardMode ffMode) {
				switch (ffMode) {
				case NO_FF:
					return FALSE;
				case FF_ONLY:
					return ONLY;
				default:
					return TRUE;
				}
			}
		}

		/**
		 * Map from <code>FastForwardMode.Merge</code> to
		 * <code>FastForwardMode</code>.
		 *
		 * @param ffMode
		 *            the <code>FastForwardMode.Merge</code> value to be mapped
		 * @return the mapped <code>FastForwardMode</code> value
		 */
		public static FastForwardMode valueOf(Merge ffMode) {
			switch (ffMode) {
			case FALSE:
				return NO_FF;
			case ONLY:
				return FF_ONLY;
			default:
				return FF;
			}
		}
	}

	private Boolean commit;

	/**
	 * Constructor for MergeCommand.
	 *
	 * @param repo
	 *            the {@link Repository}
	 */
	protected MergeCommand(Repository repo) {
		super(repo);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Execute the {@code Merge} command with all the options and parameters
	 * collected by the setter methods (e.g. {@link #include(Ref)}) of this
	 * class. Each instance of this class should only be used for one invocation
	 * of the command. Don't call this method twice on an instance.
	 */
	@Override
	@SuppressWarnings("boxing")
	public MergeResult call() throws GitAPIException, NoHeadException,
			ConcurrentRefUpdateException, CheckoutConflictException,
			InvalidMergeHeadsException, WrongRepositoryStateException, NoMessageException {
		checkCallable();
		fallBackToConfiguration();
		checkParameters();

		DirCacheCheckout dco = null;
		try (RevWalk revWalk = new RevWalk(repo)) {
			Ref head = repo.exactRef(Constants.HEAD);
			if (head == null)
				throw new NoHeadException(
						JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);
			StringBuilder refLogMessage = new StringBuilder("merge "); //$NON-NLS-1$

			// Check for FAST_FORWARD, ALREADY_UP_TO_DATE

			// we know for now there is only one commit
			Ref ref = commits.get(0);

			refLogMessage.append(ref.getName());

			// handle annotated tags
			ref = repo.getRefDatabase().peel(ref);
			ObjectId objectId = ref.getPeeledObjectId();
			if (objectId == null)
				objectId = ref.getObjectId();

			RevCommit srcCommit = revWalk.lookupCommit(objectId);

			ObjectId headId = head.getObjectId();
			if (headId == null) {
				revWalk.parseHeaders(srcCommit);
				dco = new DirCacheCheckout(repo,
						repo.lockDirCache(), srcCommit.getTree());
				dco.setFailOnConflict(true);
				dco.setProgressMonitor(monitor);
				dco.checkout();
				RefUpdate refUpdate = repo
						.updateRef(head.getTarget().getName());
				refUpdate.setNewObjectId(objectId);
				refUpdate.setExpectedOldObjectId(null);
				refUpdate.setRefLogMessage("initial pull", false); //$NON-NLS-1$
				if (refUpdate.update() != Result.NEW)
					throw new NoHeadException(
							JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);
				setCallable(false);
				return new MergeResult(srcCommit, srcCommit, new ObjectId[] {
						null, srcCommit }, MergeStatus.FAST_FORWARD,
						mergeStrategy, null, null);
			}

			RevCommit headCommit = revWalk.lookupCommit(headId);

			if (revWalk.isMergedInto(srcCommit, headCommit)) {
				setCallable(false);
				return new MergeResult(headCommit, srcCommit, new ObjectId[] {
						headCommit, srcCommit },
						MergeStatus.ALREADY_UP_TO_DATE, mergeStrategy, null, null);
			} else if (revWalk.isMergedInto(headCommit, srcCommit)
					&& fastForwardMode != FastForwardMode.NO_FF) {
				// FAST_FORWARD detected: skip doing a real merge but only
				// update HEAD
				refLogMessage.append(": " + MergeStatus.FAST_FORWARD); //$NON-NLS-1$
				dco = new DirCacheCheckout(repo,
						headCommit.getTree(), repo.lockDirCache(),
						srcCommit.getTree());
				dco.setProgressMonitor(monitor);
				dco.setFailOnConflict(true);
				dco.checkout();
				String msg = null;
				ObjectId newHead, base = null;
				MergeStatus mergeStatus = null;
				if (!squash) {
					updateHead(refLogMessage, srcCommit, headId);
					newHead = base = srcCommit;
					mergeStatus = MergeStatus.FAST_FORWARD;
				} else {
					msg = JGitText.get().squashCommitNotUpdatingHEAD;
					newHead = base = headId;
					mergeStatus = MergeStatus.FAST_FORWARD_SQUASHED;
					List<RevCommit> squashedCommits = RevWalkUtils.find(
							revWalk, srcCommit, headCommit);
					String squashMessage = new SquashMessageFormatter().format(
							squashedCommits, head);
					repo.writeSquashCommitMsg(squashMessage);
				}
				setCallable(false);
				return new MergeResult(newHead, base, new ObjectId[] {
						headCommit, srcCommit }, mergeStatus, mergeStrategy,
						null, msg);
			} else {
				if (fastForwardMode == FastForwardMode.FF_ONLY) {
					return new MergeResult(headCommit, srcCommit,
							new ObjectId[] { headCommit, srcCommit },
							MergeStatus.ABORTED, mergeStrategy, null, null);
				}
				String mergeMessage = ""; //$NON-NLS-1$
				if (!squash) {
					if (message != null)
						mergeMessage = message;
					else
						mergeMessage = new MergeMessageFormatter().format(
							commits, head);
					repo.writeMergeCommitMsg(mergeMessage);
					repo.writeMergeHeads(Arrays.asList(ref.getObjectId()));
				} else {
					List<RevCommit> squashedCommits = RevWalkUtils.find(
							revWalk, srcCommit, headCommit);
					String squashMessage = new SquashMessageFormatter().format(
							squashedCommits, head);
					repo.writeSquashCommitMsg(squashMessage);
				}
				Merger merger = mergeStrategy.newMerger(repo);
				merger.setProgressMonitor(monitor);
				boolean noProblems;
				Map<String, org.eclipse.jgit.merge.MergeResult<?>> lowLevelResults = null;
				Map<String, MergeFailureReason> failingPaths = null;
				List<String> unmergedPaths = null;
				if (merger instanceof ResolveMerger) {
					ResolveMerger resolveMerger = (ResolveMerger) merger;
					resolveMerger.setCommitNames(new String[] {
							"BASE", "HEAD", ref.getName() }); //$NON-NLS-1$ //$NON-NLS-2$
					resolveMerger.setWorkingTreeIterator(new FileTreeIterator(repo));
					noProblems = merger.merge(headCommit, srcCommit);
					lowLevelResults = resolveMerger
							.getMergeResults();
					failingPaths = resolveMerger.getFailingPaths();
					unmergedPaths = resolveMerger.getUnmergedPaths();
					if (!resolveMerger.getModifiedFiles().isEmpty()) {
						repo.fireEvent(new WorkingTreeModifiedEvent(
								resolveMerger.getModifiedFiles(), null));
					}
				} else
					noProblems = merger.merge(headCommit, srcCommit);
				refLogMessage.append(": Merge made by "); //$NON-NLS-1$
				if (!revWalk.isMergedInto(headCommit, srcCommit))
					refLogMessage.append(mergeStrategy.getName());
				else
					refLogMessage.append("recursive"); //$NON-NLS-1$
				refLogMessage.append('.');
				if (noProblems) {
					dco = new DirCacheCheckout(repo,
							headCommit.getTree(), repo.lockDirCache(),
							merger.getResultTreeId());
					dco.setFailOnConflict(true);
					dco.setProgressMonitor(monitor);
					dco.checkout();

					String msg = null;
					ObjectId newHeadId = null;
					MergeStatus mergeStatus = null;
					if (!commit && squash) {
						mergeStatus = MergeStatus.MERGED_SQUASHED_NOT_COMMITTED;
					}
					if (!commit && !squash) {
						mergeStatus = MergeStatus.MERGED_NOT_COMMITTED;
					}
					if (commit && !squash) {
						try (Git git = new Git(getRepository())) {
							newHeadId = git.commit()
									.setReflogComment(refLogMessage.toString())
									.setInsertChangeId(insertChangeId)
									.call().getId();
						}
						mergeStatus = MergeStatus.MERGED;
						getRepository().autoGC(monitor);
					}
					if (commit && squash) {
						msg = JGitText.get().squashCommitNotUpdatingHEAD;
						newHeadId = headCommit.getId();
						mergeStatus = MergeStatus.MERGED_SQUASHED;
					}
					return new MergeResult(newHeadId, null,
							new ObjectId[] { headCommit.getId(),
									srcCommit.getId() }, mergeStatus,
							mergeStrategy, null, msg);
				}
				if (failingPaths != null) {
					repo.writeMergeCommitMsg(null);
					repo.writeMergeHeads(null);
					return new MergeResult(null, merger.getBaseCommitId(),
							new ObjectId[] { headCommit.getId(),
									srcCommit.getId() },
							MergeStatus.FAILED, mergeStrategy, lowLevelResults,
							failingPaths, null);
				}
				String mergeMessageWithConflicts = new MergeMessageFormatter()
						.formatWithConflicts(mergeMessage, unmergedPaths);
				repo.writeMergeCommitMsg(mergeMessageWithConflicts);
				return new MergeResult(null, merger.getBaseCommitId(),
						new ObjectId[] { headCommit.getId(),
								srcCommit.getId() },
						MergeStatus.CONFLICTING, mergeStrategy, lowLevelResults,
						null);
			}
		} catch (org.eclipse.jgit.errors.CheckoutConflictException e) {
			List<String> conflicts = (dco == null) ? Collections
					.<String> emptyList() : dco.getConflicts();
			throw new CheckoutConflictException(conflicts, e);
		} catch (IOException e) {
			throw new JGitInternalException(
					MessageFormat.format(
							JGitText.get().exceptionCaughtDuringExecutionOfMergeCommand,
							e), e);
		}
	}

	private void checkParameters() throws InvalidMergeHeadsException {
		if (squash.booleanValue() && fastForwardMode == FastForwardMode.NO_FF) {
			throw new JGitInternalException(
					JGitText.get().cannotCombineSquashWithNoff);
		}

		if (commits.size() != 1)
			throw new InvalidMergeHeadsException(
					commits.isEmpty() ? JGitText.get().noMergeHeadSpecified
							: MessageFormat.format(
									JGitText.get().mergeStrategyDoesNotSupportHeads,
									mergeStrategy.getName(),
									Integer.valueOf(commits.size())));
	}

	/**
	 * Use values from the configuration if they have not been explicitly
	 * defined via the setters
	 */
	private void fallBackToConfiguration() {
		MergeConfig config = MergeConfig.getConfigForCurrentBranch(repo);
		if (squash == null)
			squash = Boolean.valueOf(config.isSquash());
		if (commit == null)
			commit = Boolean.valueOf(config.isCommit());
		if (fastForwardMode == null)
			fastForwardMode = config.getFastForwardMode();
	}

	private void updateHead(StringBuilder refLogMessage, ObjectId newHeadId,
			ObjectId oldHeadID) throws IOException,
			ConcurrentRefUpdateException {
		RefUpdate refUpdate = repo.updateRef(Constants.HEAD);
		refUpdate.setNewObjectId(newHeadId);
		refUpdate.setRefLogMessage(refLogMessage.toString(), false);
		refUpdate.setExpectedOldObjectId(oldHeadID);
		Result rc = refUpdate.update();
		switch (rc) {
		case NEW:
		case FAST_FORWARD:
			return;
		case REJECTED:
		case LOCK_FAILURE:
			throw new ConcurrentRefUpdateException(
					JGitText.get().couldNotLockHEAD, refUpdate.getRef(), rc);
		default:
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().updatingRefFailed, Constants.HEAD,
					newHeadId.toString(), rc));
		}
	}

	/**
	 * Set merge strategy
	 *
	 * @param mergeStrategy
	 *            the {@link MergeStrategy} to be used
	 * @return {@code this}
	 */
	public org.eclipse.jgit.api.MergeCommand setStrategy(MergeStrategy mergeStrategy) {
		checkCallable();
		this.mergeStrategy = mergeStrategy;
		return this;
	}

	/**
	 * Reference to a commit to be merged with the current head
	 *
	 * @param aCommit
	 *            a reference to a commit which is merged with the current head
	 * @return {@code this}
	 */
	public org.eclipse.jgit.api.MergeCommand include(Ref aCommit) {
		checkCallable();
		commits.add(aCommit);
		return this;
	}

	/**
	 * Id of a commit which is to be merged with the current head
	 *
	 * @param aCommit
	 *            the Id of a commit which is merged with the current head
	 * @return {@code this}
	 */
	public org.eclipse.jgit.api.MergeCommand include(AnyObjectId aCommit) {
		return include(aCommit.getName(), aCommit);
	}

	/**
	 * Include a commit
	 *
	 * @param name
	 *            a name of a {@code Ref} pointing to the commit
	 * @param aCommit
	 *            the Id of a commit which is merged with the current head
	 * @return {@code this}
	 */
	public org.eclipse.jgit.api.MergeCommand include(String name, AnyObjectId aCommit) {
		return include(new ObjectIdRef.Unpeeled(Storage.LOOSE, name,
				aCommit.copy()));
	}

	/**
	 * If <code>true</code>, will prepare the next commit in working tree and
	 * index as if a real merge happened, but do not make the commit or move the
	 * HEAD. Otherwise, perform the merge and commit the result.
	 * <p>
	 * In case the merge was successful but this flag was set to
	 * <code>true</code> a {@link MergeResult} with status
	 * {@link MergeStatus#MERGED_SQUASHED} or
	 * {@link MergeStatus#FAST_FORWARD_SQUASHED}
	 * is returned.
	 *
	 * @param squash
	 *            whether to squash commits or not
	 * @return {@code this}
	 * @since 2.0
	 */
	public org.eclipse.jgit.api.MergeCommand setSquash(boolean squash) {
		checkCallable();
		this.squash = Boolean.valueOf(squash);
		return this;
	}

	/**
	 * Sets the fast forward mode.
	 *
	 * @param fastForwardMode
	 *            corresponds to the --ff/--no-ff/--ff-only options. If
	 *            {@code null} use the value of the {@code merge.ff} option
	 *            configured in git config. If this option is not configured
	 *            --ff is the built-in default.
	 * @return {@code this}
	 * @since 2.2
	 */
	public org.eclipse.jgit.api.MergeCommand setFastForward(
			@Nullable FastForwardMode fastForwardMode) {
		checkCallable();
		this.fastForwardMode = fastForwardMode;
		return this;
	}

	/**
	 * Controls whether the merge command should automatically commit after a
	 * successful merge
	 *
	 * @param commit
	 *            <code>true</code> if this command should commit (this is the
	 *            default behavior). <code>false</code> if this command should
	 *            not commit. In case the merge was successful but this flag was
	 *            set to <code>false</code> a
	 *            {@link MergeResult} with type
	 *            {@link MergeResult} with status
	 *            {@link MergeStatus#MERGED_NOT_COMMITTED}
	 *            is returned
	 * @return {@code this}
	 * @since 3.0
	 */
	public org.eclipse.jgit.api.MergeCommand setCommit(boolean commit) {
		this.commit = Boolean.valueOf(commit);
		return this;
	}

	/**
	 * Set the commit message to be used for the merge commit (in case one is
	 * created)
	 *
	 * @param message
	 *            the message to be used for the merge commit
	 * @return {@code this}
	 * @since 3.5
	 */
	public org.eclipse.jgit.api.MergeCommand setMessage(String message) {
		this.message = message;
		return this;
	}

	/**
	 * If set to true a change id will be inserted into the commit message
	 *
	 * An existing change id is not replaced. An initial change id (I000...)
	 * will be replaced by the change id.
	 *
	 * @param insertChangeId
	 *            whether to insert a change id
	 * @return {@code this}
	 * @since 5.0
	 */
	public org.eclipse.jgit.api.MergeCommand setInsertChangeId(boolean insertChangeId) {
		checkCallable();
		this.insertChangeId = insertChangeId;
		return this;
	}

	/**
	 * The progress monitor associated with the diff operation. By default, this
	 * is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 * @param monitor
	 *            A progress monitor
	 * @return this instance
	 * @since 4.2
	 */
	public org.eclipse.jgit.api.MergeCommand setProgressMonitor(ProgressMonitor monitor) {
		if (monitor == null) {
			monitor = NullProgressMonitor.INSTANCE;
		}
		this.monitor = monitor;
		return this;
	}
}
