/*
 * Copyright (C) 2010, 2013, Mathias Kinzler <mathias.kinzler@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.List;
import java.util.Map;

/**
 * The result of a {@link org.eclipse.jgit.api.RebaseCommand} execution
 */
public class RebaseResult {
	/**
	 * The overall status
	 */
	public enum Status {
		/**
		 * Rebase was successful, HEAD points to the new commit
		 */
		OK {
			@Override
			public boolean isSuccessful() {
				return true;
			}
		},
		/**
		 * Aborted; the original HEAD was restored
		 */
		ABORTED {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * Stopped due to a conflict; must either abort or resolve or skip
		 */
		STOPPED {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * Stopped for editing in the context of an interactive rebase
		 *
		 * @since 3.2
		 */
		EDIT {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * Failed; the original HEAD was restored
		 */
		FAILED {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * The repository contains uncommitted changes and the rebase is not a
		 * fast-forward
		 *
		 * @since 3.2
		 */
		UNCOMMITTED_CHANGES {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * Conflicts: checkout of target HEAD failed
		 */
		CONFLICTS {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},
		/**
		 * Already up-to-date
		 */
		UP_TO_DATE {
			@Override
			public boolean isSuccessful() {
				return true;
			}
		},
		/**
		 * Fast-forward, HEAD points to the new commit
		 */
		FAST_FORWARD {
			@Override
			public boolean isSuccessful() {
				return true;
			}
		},

		/**
		 * Continue with nothing left to commit (possibly want skip).
		 *
		 * @since 2.0
		 */
		NOTHING_TO_COMMIT {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},

		/**
		 * Interactive rebase has been prepared
		 * @since 3.2
		 */
		INTERACTIVE_PREPARED {
			@Override
			public boolean isSuccessful() {
				return false;
			}
		},

		/**
		 * Applying stash resulted in conflicts
		 *
		 * @since 3.2
		 */
		STASH_APPLY_CONFLICTS {
			@Override
			public boolean isSuccessful() {
				return true;
			}
		};

		/**
		 * @return whether the status indicates a successful result
		 */
		public abstract boolean isSuccessful();
	}

	static final org.eclipse.jgit.api.RebaseResult OK_RESULT = new org.eclipse.jgit.api.RebaseResult(Status.OK);

	static final org.eclipse.jgit.api.RebaseResult ABORTED_RESULT = new org.eclipse.jgit.api.RebaseResult(Status.ABORTED);

	static final org.eclipse.jgit.api.RebaseResult UP_TO_DATE_RESULT = new org.eclipse.jgit.api.RebaseResult(
			Status.UP_TO_DATE);

	static final org.eclipse.jgit.api.RebaseResult FAST_FORWARD_RESULT = new org.eclipse.jgit.api.RebaseResult(
			Status.FAST_FORWARD);

	static final org.eclipse.jgit.api.RebaseResult NOTHING_TO_COMMIT_RESULT = new org.eclipse.jgit.api.RebaseResult(
			Status.NOTHING_TO_COMMIT);

	static final org.eclipse.jgit.api.RebaseResult INTERACTIVE_PREPARED_RESULT =  new org.eclipse.jgit.api.RebaseResult(
			Status.INTERACTIVE_PREPARED);

	static final org.eclipse.jgit.api.RebaseResult STASH_APPLY_CONFLICTS_RESULT = new org.eclipse.jgit.api.RebaseResult(
			Status.STASH_APPLY_CONFLICTS);

	private final Status status;

	private final RevCommit currentCommit;

	private Map<String, MergeFailureReason> failingPaths;

	private List<String> conflicts;

	private List<String> uncommittedChanges;

	private RebaseResult(Status status) {
		this.status = status;
		currentCommit = null;
	}

	private RebaseResult(Status status, RevCommit commit) {
		this.status = status;
		currentCommit = commit;
	}

	/**
	 * Create <code>RebaseResult</code>
	 *
	 * @param status
	 * @param commit
	 *            current commit
	 * @return the RebaseResult
	 */
	static org.eclipse.jgit.api.RebaseResult result(org.eclipse.jgit.api.RebaseResult.Status status,
	                                                RevCommit commit) {
		return new org.eclipse.jgit.api.RebaseResult(status, commit);
	}

	/**
	 * Create <code>RebaseResult</code> with status {@link Status#FAILED}
	 *
	 * @param failingPaths
	 *            list of paths causing this rebase to fail
	 * @return the RebaseResult
	 */
	static org.eclipse.jgit.api.RebaseResult failed(
			Map<String, MergeFailureReason> failingPaths) {
		org.eclipse.jgit.api.RebaseResult result = new org.eclipse.jgit.api.RebaseResult(Status.FAILED);
		result.failingPaths = failingPaths;
		return result;
	}

	/**
	 * Create <code>RebaseResult</code> with status {@link Status#CONFLICTS}
	 *
	 * @param conflicts
	 *            the list of conflicting paths
	 * @return the RebaseResult
	 */
	static org.eclipse.jgit.api.RebaseResult conflicts(List<String> conflicts) {
		org.eclipse.jgit.api.RebaseResult result = new org.eclipse.jgit.api.RebaseResult(Status.CONFLICTS);
		result.conflicts = conflicts;
		return result;
	}

	/**
	 * Create <code>RebaseResult</code> with status
	 * {@link Status#UNCOMMITTED_CHANGES}
	 *
	 * @param uncommittedChanges
	 *            the list of paths
	 * @return the RebaseResult
	 */
	static org.eclipse.jgit.api.RebaseResult uncommittedChanges(List<String> uncommittedChanges) {
		org.eclipse.jgit.api.RebaseResult result = new org.eclipse.jgit.api.RebaseResult(Status.UNCOMMITTED_CHANGES);
		result.uncommittedChanges = uncommittedChanges;
		return result;
	}

	/**
	 * Get the status
	 *
	 * @return the overall status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * Get the current commit if status is
	 * {@link org.eclipse.jgit.api.RebaseResult.Status#STOPPED}, otherwise
	 * <code>null</code>
	 *
	 * @return the current commit if status is
	 *         {@link org.eclipse.jgit.api.RebaseResult.Status#STOPPED},
	 *         otherwise <code>null</code>
	 */
	public RevCommit getCurrentCommit() {
		return currentCommit;
	}

	/**
	 * Get the list of paths causing this rebase to fail
	 *
	 * @return the list of paths causing this rebase to fail (see
	 *         {@link org.eclipse.jgit.merge.ResolveMerger#getFailingPaths()}
	 *         for details) if status is
	 *         {@link org.eclipse.jgit.api.RebaseResult.Status#FAILED},
	 *         otherwise <code>null</code>
	 */
	public Map<String, MergeFailureReason> getFailingPaths() {
		return failingPaths;
	}

	/**
	 * Get the list of conflicts
	 *
	 * @return the list of conflicts if status is
	 *         {@link org.eclipse.jgit.api.RebaseResult.Status#CONFLICTS}
	 */
	public List<String> getConflicts() {
		return conflicts;
	}

	/**
	 * <p>Getter for the field <code>uncommittedChanges</code>.</p>
	 *
	 * @return the list of uncommitted changes if status is
	 *         {@link org.eclipse.jgit.api.RebaseResult.Status#UNCOMMITTED_CHANGES}
	 * @since 3.2
	 */
	public List<String> getUncommittedChanges() {
		return uncommittedChanges;
	}

}
