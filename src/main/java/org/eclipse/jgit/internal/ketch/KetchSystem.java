/*
 * Copyright (C) 2016, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.ketch;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.time.MonotonicClock;
import org.eclipse.jgit.util.time.MonotonicSystemClock;
import org.eclipse.jgit.util.time.ProposedTimestamp;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.eclipse.jgit.internal.ketch.KetchConstants.*;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_NAME;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_REMOTE;

/**
 * Ketch system-wide configuration.
 * <p>
 * This class provides useful defaults for testing and small proof of concepts.
 * Full scale installations are expected to subclass and override methods to
 * provide consistent configuration across all managed repositories.
 * <p>
 * Servers should configure their own
 * {@link ScheduledExecutorService}.
 */
public class KetchSystem {
	private static final Random RNG = new Random();

	/**
	 * Get default executor, one thread per available processor.
	 *
	 * @return default executor, one thread per available processor.
	 */
	public static ScheduledExecutorService defaultExecutor() {
		return DefaultExecutorHolder.I;
	}

	private final ScheduledExecutorService executor;
	private final MonotonicClock clock;
	private final String txnNamespace;
	private final String txnAccepted;
	private final String txnCommitted;
	private final String txnStage;

	/**
	 * Create a default system with a thread pool of 1 thread per CPU.
	 */
	public KetchSystem() {
		this(defaultExecutor(), new MonotonicSystemClock(), DEFAULT_TXN_NAMESPACE);
	}

	/**
	 * Create a Ketch system with the provided executor service.
	 *
	 * @param executor
	 *            thread pool to run background operations.
	 * @param clock
	 *            clock to create timestamps.
	 * @param txnNamespace
	 *            reference namespace for the RefTree graph and associated
	 *            transaction state. Must begin with {@code "refs/"} and end
	 *            with {@code '/'}, for example {@code "refs/txn/"}.
	 */
	public KetchSystem(ScheduledExecutorService executor, MonotonicClock clock,
			String txnNamespace) {
		this.executor = executor;
		this.clock = clock;
		this.txnNamespace = txnNamespace;
		this.txnAccepted = txnNamespace + ACCEPTED;
		this.txnCommitted = txnNamespace + COMMITTED;
		this.txnStage = txnNamespace + STAGE;
	}

	/**
	 * Get executor to perform background operations.
	 *
	 * @return executor to perform background operations.
	 */
	public ScheduledExecutorService getExecutor() {
		return executor;
	}

	/**
	 * Get clock to obtain timestamps from.
	 *
	 * @return clock to obtain timestamps from.
	 */
	public MonotonicClock getClock() {
		return clock;
	}

	/**
	 * Get how long the leader will wait for the {@link #getClock()}'s
	 * {@code ProposedTimestamp} used in commits proposed to the RefTree graph
	 * ({@link #getTxnAccepted()})
	 *
	 * @return how long the leader will wait for the {@link #getClock()}'s
	 *         {@code ProposedTimestamp} used in commits proposed to the RefTree
	 *         graph ({@link #getTxnAccepted()}). Defaults to 5 seconds.
	 */
	public Duration getMaxWaitForMonotonicClock() {
		return Duration.ofSeconds(5);
	}

	/**
	 * Whether elections should require monotonically increasing commit
	 * timestamps
	 *
	 * @return {@code true} if elections should require monotonically increasing
	 *         commit timestamps. This requires a very good
	 *         {@link MonotonicClock}.
	 */
	public boolean requireMonotonicLeaderElections() {
		return false;
	}

	/**
	 * Get the namespace used for the RefTree graph and transaction management.
	 *
	 * @return reference namespace such as {@code "refs/txn/"}.
	 */
	public String getTxnNamespace() {
		return txnNamespace;
	}

	/**
	 * Get name of the accepted RefTree graph.
	 *
	 * @return name of the accepted RefTree graph.
	 */
	public String getTxnAccepted() {
		return txnAccepted;
	}

	/**
	 * Get name of the committed RefTree graph.
	 *
	 * @return name of the committed RefTree graph.
	 */
	public String getTxnCommitted() {
		return txnCommitted;
	}

	/**
	 * Get prefix for staged objects, e.g. {@code "refs/txn/stage/"}.
	 *
	 * @return prefix for staged objects, e.g. {@code "refs/txn/stage/"}.
	 */
	public String getTxnStage() {
		return txnStage;
	}

	/**
	 * Create new committer {@code PersonIdent} for ketch system
	 *
	 * @param time
	 *            timestamp for the committer.
	 * @return identity line for the committer header of a RefTreeGraph.
	 */
	public PersonIdent newCommitter(ProposedTimestamp time) {
		String name = "ketch"; //$NON-NLS-1$
		String email = "ketch@system"; //$NON-NLS-1$
		return new PersonIdent(name, email, time);
	}

	/**
	 * Construct a random tag to identify a candidate during leader election.
	 * <p>
	 * Multiple processes trying to elect themselves leaders at exactly the same
	 * time (rounded to seconds) using the same
	 * {@link #newCommitter(ProposedTimestamp)} identity strings, for the same
	 * term, may generate the same ObjectId for the election commit and falsely
	 * assume they have both won.
	 * <p>
	 * Candidates add this tag to their election ballot commit to disambiguate
	 * the election. The tag only needs to be unique for a given triplet of
	 * {@link #newCommitter(ProposedTimestamp)}, system time (rounded to
	 * seconds), and term. If every replica in the system uses a unique
	 * {@code newCommitter} (such as including the host name after the
	 * {@code "@"} in the email address) the tag could be the empty string.
	 * <p>
	 * The default implementation generates a few bytes of random data.
	 *
	 * @return unique tag; null or empty string if {@code newCommitter()} is
	 *         sufficiently unique to identify the leader.
	 */
	@Nullable
	public String newLeaderTag() {
		int n = RNG.nextInt(1 << (6 * 4));
		return String.format("%06x", Integer.valueOf(n)); //$NON-NLS-1$
	}

	/**
	 * Construct the KetchLeader instance of a repository.
	 *
	 * @param repo
	 *            local repository stored by the leader.
	 * @return leader instance.
	 * @throws URISyntaxException
	 *             a follower configuration contains an unsupported URI.
	 */
	public KetchLeader createLeader(Repository repo)
			throws URISyntaxException {
		KetchLeader leader = new KetchLeader(this) {
			@Override
			protected Repository openRepository() {
				repo.incrementOpen();
				return repo;
			}
		};
		leader.setReplicas(createReplicas(leader, repo));
		return leader;
	}

	/**
	 * Get the collection of replicas for a repository.
	 * <p>
	 * The collection of replicas must include the local repository.
	 *
	 * @param leader
	 *            the leader driving these replicas.
	 * @param repo
	 *            repository to get the replicas of.
	 * @return collection of replicas for the specified repository.
	 * @throws URISyntaxException
	 *             a configured URI is invalid.
	 */
	protected List<KetchReplica> createReplicas(KetchLeader leader,
			Repository repo) throws URISyntaxException {
		List<KetchReplica> replicas = new ArrayList<>();
		Config cfg = repo.getConfig();
		String localName = getLocalName(cfg);
		for (String name : cfg.getSubsections(CONFIG_KEY_REMOTE)) {
			if (!hasParticipation(cfg, name)) {
				continue;
			}

			ReplicaConfig kc = ReplicaConfig.newFromConfig(cfg, name);
			if (name.equals(localName)) {
				replicas.add(new LocalReplica(leader, name, kc));
				continue;
			}

			RemoteConfig rc = new RemoteConfig(cfg, name);
			List<URIish> uris = rc.getPushURIs();
			if (uris.isEmpty()) {
				uris = rc.getURIs();
			}
			for (URIish uri : uris) {
				String n = uris.size() == 1 ? name : uri.getHost();
				replicas.add(new RemoteGitReplica(leader, n, uri, kc, rc));
			}
		}
		return replicas;
	}

	private static boolean hasParticipation(Config cfg, String name) {
		return cfg.getString(CONFIG_KEY_REMOTE, name, CONFIG_KEY_TYPE) != null;
	}

	private static String getLocalName(Config cfg) {
		return cfg.getString(CONFIG_SECTION_KETCH, null, CONFIG_KEY_NAME);
	}

	static class DefaultExecutorHolder {
		static final ScheduledExecutorService I = create();

		private static ScheduledExecutorService create() {
			int cores = Runtime.getRuntime().availableProcessors();
			int threads = Math.max(5, cores);
			return Executors.newScheduledThreadPool(
				threads,
				new ThreadFactory() {
					private final AtomicInteger threadCnt = new AtomicInteger();

					@Override
					public Thread newThread(Runnable r) {
						int id = threadCnt.incrementAndGet();
						Thread thr = new Thread(r);
						thr.setName("KetchExecutor-" + id); //$NON-NLS-1$
						return thr;
					}
				});
		}

		private DefaultExecutorHolder() {
		}
	}

}
