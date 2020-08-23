/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.transport;

import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.PackLock;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.*;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.GitProtocolConstants.MultiAck;
import org.eclipse.jgit.transport.PacketLineIn.AckNackResult;
import org.eclipse.jgit.util.TemporaryBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * Fetch implementation using the native Git pack transfer service.
 * <p>
 * This is the canonical implementation for transferring objects from the remote
 * repository to the local repository by talking to the 'git-upload-pack'
 * service. Objects are packed on the remote side into a pack file and then sent
 * down the pipe to us.
 * <p>
 * This connection requires only a bi-directional pipe or socket, and thus is
 * easily wrapped up into a local process pipe, anonymous TCP socket, or a
 * command executed through an SSH tunnel.
 * <p>
 * If {@link BasePackConnection#statelessRPC} is
 * {@code true}, this connection can be tunneled over a request-response style
 * RPC system like HTTP. The RPC call boundary is determined by this class
 * switching from writing to the OutputStream to reading from the InputStream.
 * <p>
 * Concrete implementations should just call
 * {@link #init(InputStream, OutputStream)} and
 * {@link #readAdvertisedRefs()} methods in constructor or before any use. They
 * should also handle resources releasing in {@link #close()} method if needed.
 */
public abstract class BasePackFetchConnection extends BasePackConnection
		implements FetchConnection {
	/**
	 * Maximum number of 'have' lines to send before giving up.
	 * <p>
	 * During {@link #negotiate(ProgressMonitor)} we send at most this many
	 * commits to the remote peer as 'have' lines without an ACK response before
	 * we give up.
	 */
	private static final int MAX_HAVES = 256;

	/**
	 * Amount of data the client sends before starting to read.
	 * <p>
	 * Any output stream given to the client must be able to buffer this many
	 * bytes before the client will stop writing and start reading from the
	 * input stream. If the output stream blocks before this many bytes are in
	 * the send queue, the system will deadlock.
	 */
	protected static final int MIN_CLIENT_BUFFER = 2 * 32 * 46 + 8;

	/**
	 * Include tags if we are also including the referenced objects.
	 * @since 2.0
	 */
	public static final String OPTION_INCLUDE_TAG = GitProtocolConstants.OPTION_INCLUDE_TAG;

	/**
	 * Multi-ACK support for improved negotiation.
	 * @since 2.0
	 */
	public static final String OPTION_MULTI_ACK = GitProtocolConstants.OPTION_MULTI_ACK;

	/**
	 * Multi-ACK detailed support for improved negotiation.
	 * @since 2.0
	 */
	public static final String OPTION_MULTI_ACK_DETAILED = GitProtocolConstants.OPTION_MULTI_ACK_DETAILED;

	/**
	 * The client supports packs with deltas but not their bases.
	 * @since 2.0
	 */
	public static final String OPTION_THIN_PACK = GitProtocolConstants.OPTION_THIN_PACK;

	/**
	 * The client supports using the side-band for progress messages.
	 * @since 2.0
	 */
	public static final String OPTION_SIDE_BAND = GitProtocolConstants.OPTION_SIDE_BAND;

	/**
	 * The client supports using the 64K side-band for progress messages.
	 * @since 2.0
	 */
	public static final String OPTION_SIDE_BAND_64K = GitProtocolConstants.OPTION_SIDE_BAND_64K;

	/**
	 * The client supports packs with OFS deltas.
	 * @since 2.0
	 */
	public static final String OPTION_OFS_DELTA = GitProtocolConstants.OPTION_OFS_DELTA;

	/**
	 * The client supports shallow fetches.
	 * @since 2.0
	 */
	public static final String OPTION_SHALLOW = GitProtocolConstants.OPTION_SHALLOW;

	/**
	 * The client does not want progress messages and will ignore them.
	 * @since 2.0
	 */
	public static final String OPTION_NO_PROGRESS = GitProtocolConstants.OPTION_NO_PROGRESS;

	/**
	 * The client supports receiving a pack before it has sent "done".
	 * @since 2.0
	 */
	public static final String OPTION_NO_DONE = GitProtocolConstants.OPTION_NO_DONE;

	/**
	 * The client supports fetching objects at the tip of any ref, even if not
	 * advertised.
	 * @since 3.1
	 */
	public static final String OPTION_ALLOW_TIP_SHA1_IN_WANT = GitProtocolConstants.OPTION_ALLOW_TIP_SHA1_IN_WANT;

	/**
	 * The client supports fetching objects that are reachable from a tip of a
	 * ref that is allowed to fetch.
	 * @since 4.1
	 */
	public static final String OPTION_ALLOW_REACHABLE_SHA1_IN_WANT = GitProtocolConstants.OPTION_ALLOW_REACHABLE_SHA1_IN_WANT;

	/**
	 * The client specified a filter expression.
	 *
	 * @since 5.0
	 */
	public static final String OPTION_FILTER = GitProtocolConstants.OPTION_FILTER;

	private final RevWalk walk;

	/** All commits that are immediately reachable by a local ref. */
	private RevCommitList<RevCommit> reachableCommits;

	/** Marks an object as having all its dependencies. */
	final RevFlag REACHABLE;

	/** Marks a commit known to both sides of the connection. */
	final RevFlag COMMON;

	/** Like {@link #COMMON} but means its also in {@link #pckState}. */
	private final RevFlag STATE;

	/** Marks a commit listed in the advertised refs. */
	final RevFlag ADVERTISED;

	private MultiAck multiAck = MultiAck.OFF;

	private boolean thinPack;

	private boolean sideband;

	private boolean includeTags;

	private boolean allowOfsDelta;

	private boolean noDone;

	private boolean noProgress;

	private String lockMessage;

	private PackLock packLock;

	private int maxHaves;

	/** RPC state, if {@link BasePackConnection#statelessRPC} is true. */
	private TemporaryBuffer.Heap state;

	private PacketLineOut pckState;

	/**
	 * Either FilterSpec.NO_FILTER for a filter that doesn't filter
	 * anything, or a filter that indicates what and what not to send to the
	 * server.
	 */
	private final FilterSpec filterSpec;

	/**
	 * Create a new connection to fetch using the native git transport.
	 *
	 * @param packTransport
	 *            the transport.
	 */
	public BasePackFetchConnection(PackTransport packTransport) {
		super(packTransport);

		if (local != null) {
			final FetchConfig cfg = getFetchConfig();
			allowOfsDelta = cfg.allowOfsDelta;
			maxHaves = cfg.maxHaves;
		} else {
			allowOfsDelta = true;
			maxHaves = Integer.MAX_VALUE;
		}

		includeTags = transport.getTagOpt() != TagOpt.NO_TAGS;
		thinPack = transport.isFetchThin();
		filterSpec = transport.getFilterSpec();

		if (local != null) {
			walk = new RevWalk(local);
			walk.setRetainBody(false);
			reachableCommits = new RevCommitList<>();
			REACHABLE = walk.newFlag("REACHABLE"); //$NON-NLS-1$
			COMMON = walk.newFlag("COMMON"); //$NON-NLS-1$
			STATE = walk.newFlag("STATE"); //$NON-NLS-1$
			ADVERTISED = walk.newFlag("ADVERTISED"); //$NON-NLS-1$

			walk.carry(COMMON);
			walk.carry(REACHABLE);
			walk.carry(ADVERTISED);
		} else {
			walk = null;
			REACHABLE = null;
			COMMON = null;
			STATE = null;
			ADVERTISED = null;
		}
	}

	static class FetchConfig {
		final boolean allowOfsDelta;

		final int maxHaves;

		FetchConfig(Config c) {
			allowOfsDelta = c.getBoolean("repack", "usedeltabaseoffset", true); //$NON-NLS-1$ //$NON-NLS-2$
			maxHaves = c.getInt("fetch", "maxhaves", Integer.MAX_VALUE); //$NON-NLS-1$ //$NON-NLS-2$
		}

		FetchConfig(boolean allowOfsDelta, int maxHaves) {
			this.allowOfsDelta = allowOfsDelta;
			this.maxHaves = maxHaves;
		}
	}

	/** {@inheritDoc} */
	@Override
	public final void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have)
			throws TransportException {
		fetch(monitor, want, have, null);
	}

	/** {@inheritDoc} */
	@Override
	public final void fetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have,
			OutputStream outputStream) throws TransportException {
		markStartedOperation();
		doFetch(monitor, want, have, outputStream);
	}

	/** {@inheritDoc} */
	@Override
	public boolean didFetchIncludeTags() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public boolean didFetchTestConnectivity() {
		return false;
	}

	/** {@inheritDoc} */
	@Override
	public void setPackLockMessage(String message) {
		lockMessage = message;
	}

	/** {@inheritDoc} */
	@Override
	public Collection<PackLock> getPackLocks() {
		if (packLock != null)
			return Collections.singleton(packLock);
		return Collections.<PackLock> emptyList();
	}

	/**
	 * Execute common ancestor negotiation and fetch the objects.
	 *
	 * @param monitor
	 *            progress monitor to receive status updates. If the monitor is
	 *            the {@link NullProgressMonitor#INSTANCE}, then the no-progress
	 *            option enabled.
	 * @param want
	 *            the advertised remote references the caller wants to fetch.
	 * @param have
	 *            additional objects to assume that already exist locally. This
	 *            will be added to the set of objects reachable from the
	 *            destination repository's references.
	 * @param outputStream
	 *            ouputStream to write sideband messages to
	 * @throws TransportException
	 *             if any exception occurs.
	 * @since 3.0
	 */
	protected void doFetch(final ProgressMonitor monitor,
			final Collection<Ref> want, final Set<ObjectId> have,
			OutputStream outputStream) throws TransportException {
		try {
			noProgress = monitor == NullProgressMonitor.INSTANCE;

			markRefsAdvertised();
			markReachable(have, maxTimeWanted(want));

			if (statelessRPC) {
				state = new TemporaryBuffer.Heap(Integer.MAX_VALUE);
				pckState = new PacketLineOut(state);
			}

			if (sendWants(want)) {
				negotiate(monitor);

				walk.dispose();
				reachableCommits = null;
				state = null;
				pckState = null;

				receivePack(monitor, outputStream);
			}
		} catch (CancelledException ce) {
			close();
			return; // Caller should test (or just know) this themselves.
		} catch (IOException | RuntimeException err) {
			close();
			throw new TransportException(err.getMessage(), err);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		if (walk != null)
			walk.close();
		super.close();
	}

	FetchConfig getFetchConfig() {
		return local.getConfig().get(FetchConfig::new);
	}

	private int maxTimeWanted(Collection<Ref> wants) {
		int maxTime = 0;
		for (Ref r : wants) {
			try {
				final RevObject obj = walk.parseAny(r.getObjectId());
				if (obj instanceof RevCommit) {
					final int cTime = ((RevCommit) obj).getCommitTime();
					if (maxTime < cTime)
						maxTime = cTime;
				}
			} catch (IOException error) {
				// We don't have it, but we want to fetch (thus fixing error).
			}
		}
		return maxTime;
	}

	private void markReachable(Set<ObjectId> have, int maxTime)
			throws IOException {
		for (Ref r : local.getRefDatabase().getRefs()) {
			ObjectId id = r.getPeeledObjectId();
			if (id == null)
				id = r.getObjectId();
			if (id == null)
				continue;
			parseReachable(id);
		}

		for (ObjectId id : local.getAdditionalHaves())
			parseReachable(id);

		for (ObjectId id : have)
			parseReachable(id);

		if (maxTime > 0) {
			// Mark reachable commits until we reach maxTime. These may
			// wind up later matching up against things we want and we
			// can avoid asking for something we already happen to have.
			//
			final Date maxWhen = new Date(maxTime * 1000L);
			walk.sort(RevSort.COMMIT_TIME_DESC);
			walk.markStart(reachableCommits);
			walk.setRevFilter(CommitTimeRevFilter.after(maxWhen));
			for (;;) {
				final RevCommit c = walk.next();
				if (c == null)
					break;
				if (c.has(ADVERTISED) && !c.has(COMMON)) {
					// This is actually going to be a common commit, but
					// our peer doesn't know that fact yet.
					//
					c.add(COMMON);
					c.carry(COMMON);
					reachableCommits.add(c);
				}
			}
		}
	}

	private void parseReachable(ObjectId id) {
		try {
			RevCommit o = walk.parseCommit(id);
			if (!o.has(REACHABLE)) {
				o.add(REACHABLE);
				reachableCommits.add(o);
			}
		} catch (IOException readError) {
			// If we cannot read the value of the ref skip it.
		}
	}

	private boolean sendWants(Collection<Ref> want) throws IOException {
		final PacketLineOut p = statelessRPC ? pckState : pckOut;
		boolean first = true;
		for (Ref r : want) {
			ObjectId objectId = r.getObjectId();
			if (objectId == null) {
				continue;
			}
			try {
				if (walk.parseAny(objectId).has(REACHABLE)) {
					// We already have this object. Asking for it is
					// not a very good idea.
					//
					continue;
				}
			} catch (IOException err) {
				// Its OK, we don't have it, but we want to fix that
				// by fetching the object from the other side.
			}

			final StringBuilder line = new StringBuilder(46);
			line.append("want "); //$NON-NLS-1$
			line.append(objectId.name());
			if (first) {
				line.append(enableCapabilities());
				first = false;
			}
			line.append('\n');
			p.writeString(line.toString());
		}
		if (first) {
			return false;
		}
		if (!filterSpec.isNoOp()) {
			p.writeString(filterSpec.filterLine());
		}
		p.end();
		outNeedsEnd = false;
		return true;
	}

	private String enableCapabilities() throws TransportException {
		final StringBuilder line = new StringBuilder();
		if (noProgress)
			wantCapability(line, OPTION_NO_PROGRESS);
		if (includeTags)
			includeTags = wantCapability(line, OPTION_INCLUDE_TAG);
		if (allowOfsDelta)
			wantCapability(line, OPTION_OFS_DELTA);

		if (wantCapability(line, OPTION_MULTI_ACK_DETAILED)) {
			multiAck = MultiAck.DETAILED;
			if (statelessRPC)
				noDone = wantCapability(line, OPTION_NO_DONE);
		} else if (wantCapability(line, OPTION_MULTI_ACK))
			multiAck = MultiAck.CONTINUE;
		else
			multiAck = MultiAck.OFF;

		if (thinPack)
			thinPack = wantCapability(line, OPTION_THIN_PACK);
		if (wantCapability(line, OPTION_SIDE_BAND_64K))
			sideband = true;
		else if (wantCapability(line, OPTION_SIDE_BAND))
			sideband = true;

		if (statelessRPC && multiAck != MultiAck.DETAILED) {
			// Our stateless RPC implementation relies upon the detailed
			// ACK status to tell us common objects for reuse in future
			// requests.  If its not enabled, we can't talk to the peer.
			//
			throw new PackProtocolException(uri, MessageFormat.format(
					JGitText.get().statelessRPCRequiresOptionToBeEnabled,
					OPTION_MULTI_ACK_DETAILED));
		}

		if (!filterSpec.isNoOp() && !wantCapability(line, OPTION_FILTER)) {
			throw new PackProtocolException(uri,
					JGitText.get().filterRequiresCapability);
		}

		addUserAgentCapability(line);
		return line.toString();
	}

	private void negotiate(ProgressMonitor monitor) throws IOException,
			CancelledException {
		final MutableObjectId ackId = new MutableObjectId();
		int resultsPending = 0;
		int havesSent = 0;
		int havesSinceLastContinue = 0;
		boolean receivedContinue = false;
		boolean receivedAck = false;
		boolean receivedReady = false;

		if (statelessRPC) {
			state.writeTo(out, null);
		}

		negotiateBegin();
		SEND_HAVES: for (;;) {
			final RevCommit c = walk.next();
			if (c == null) {
				break SEND_HAVES;
			}

			ObjectId o = c.getId();
			pckOut.writeString("have " + o.name() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
			havesSent++;
			havesSinceLastContinue++;

			if ((31 & havesSent) != 0) {
				// We group the have lines into blocks of 32, each marked
				// with a flush (aka end). This one is within a block so
				// continue with another have line.
				//
				continue;
			}

			if (monitor.isCancelled()) {
				throw new CancelledException();
			}

			pckOut.end();
			resultsPending++; // Each end will cause a result to come back.

			if (havesSent == 32 && !statelessRPC) {
				// On the first block we race ahead and try to send
				// more of the second block while waiting for the
				// remote to respond to our first block request.
				// This keeps us one block ahead of the peer.
				//
				continue;
			}

			READ_RESULT: for (;;) {
				final AckNackResult anr = pckIn.readACK(ackId);
				switch (anr) {
				case NAK:
					// More have lines are necessary to compute the
					// pack on the remote side. Keep doing that.
					//
					resultsPending--;
					break READ_RESULT;

				case ACK:
					// The remote side is happy and knows exactly what
					// to send us. There is no further negotiation and
					// we can break out immediately.
					//
					multiAck = MultiAck.OFF;
					resultsPending = 0;
					receivedAck = true;
					if (statelessRPC) {
						state.writeTo(out, null);
					}
					break SEND_HAVES;

				case ACK_CONTINUE:
				case ACK_COMMON:
				case ACK_READY:
					// The server knows this commit (ackId). We don't
					// need to send any further along its ancestry, but
					// we need to continue to talk about other parts of
					// our local history.
					//
					markCommon(walk.parseAny(ackId), anr);
					receivedAck = true;
					receivedContinue = true;
					havesSinceLastContinue = 0;
					if (anr == AckNackResult.ACK_READY) {
						receivedReady = true;
					}
					break;
				}

				if (monitor.isCancelled()) {
					throw new CancelledException();
				}
			}

			if (noDone && receivedReady) {
				break SEND_HAVES;
			}
			if (statelessRPC) {
				state.writeTo(out, null);
			}

			if ((receivedContinue && havesSinceLastContinue > MAX_HAVES)
					|| havesSent >= maxHaves) {
				// Our history must be really different from the remote's.
				// We just sent a whole slew of have lines, and it did not
				// recognize any of them. Avoid sending our entire history
				// to them by giving up early.
				//
				break SEND_HAVES;
			}
		}

		// Tell the remote side we have run out of things to talk about.
		//
		if (monitor.isCancelled()) {
			throw new CancelledException();
		}

		if (!receivedReady || !noDone) {
			// When statelessRPC is true we should always leave SEND_HAVES
			// loop above while in the middle of a request. This allows us
			// to just write done immediately.
			//
			pckOut.writeString("done\n"); //$NON-NLS-1$
			pckOut.flush();
		}

		if (!receivedAck) {
			// Apparently if we have never received an ACK earlier
			// there is one more result expected from the done we
			// just sent to the remote.
			//
			multiAck = MultiAck.OFF;
			resultsPending++;
		}

		READ_RESULT: while (resultsPending > 0 || multiAck != MultiAck.OFF) {
			final AckNackResult anr = pckIn.readACK(ackId);
			resultsPending--;
			switch (anr) {
			case NAK:
				// A NAK is a response to an end we queued earlier
				// we eat it and look for another ACK/NAK message.
				//
				break;

			case ACK:
				// A solitary ACK at this point means the remote won't
				// speak anymore, but is going to send us a pack now.
				//
				break READ_RESULT;

			case ACK_CONTINUE:
			case ACK_COMMON:
			case ACK_READY:
				// We will expect a normal ACK to break out of the loop.
				//
				multiAck = MultiAck.CONTINUE;
				break;
			}

			if (monitor.isCancelled()) {
				throw new CancelledException();
			}
		}
	}

	private void negotiateBegin() throws IOException {
		walk.resetRetain(REACHABLE, ADVERTISED);
		walk.markStart(reachableCommits);
		walk.sort(RevSort.COMMIT_TIME_DESC);
		walk.setRevFilter(new RevFilter() {
			@Override
			public RevFilter clone() {
				return this;
			}

			@Override
			public boolean include(RevWalk walker, RevCommit c) {
				final boolean remoteKnowsIsCommon = c.has(COMMON);
				if (c.has(ADVERTISED)) {
					// Remote advertised this, and we have it, hence common.
					// Whether or not the remote knows that fact is tested
					// before we added the flag. If the remote doesn't know
					// we have to still send them this object.
					//
					c.add(COMMON);
				}
				return !remoteKnowsIsCommon;
			}

			@Override
			public boolean requiresCommitBody() {
				return false;
			}
		});
	}

	private void markRefsAdvertised() {
		for (Ref r : getRefs()) {
			markAdvertised(r.getObjectId());
			if (r.getPeeledObjectId() != null)
				markAdvertised(r.getPeeledObjectId());
		}
	}

	private void markAdvertised(AnyObjectId id) {
		try {
			walk.parseAny(id).add(ADVERTISED);
		} catch (IOException readError) {
			// We probably just do not have this object locally.
		}
	}

	private void markCommon(RevObject obj, AckNackResult anr)
			throws IOException {
		if (statelessRPC && anr == AckNackResult.ACK_COMMON && !obj.has(STATE)) {
			StringBuilder s;

			s = new StringBuilder(6 + Constants.OBJECT_ID_STRING_LENGTH);
			s.append("have "); //$NON-NLS-1$
			s.append(obj.name());
			s.append('\n');
			pckState.writeString(s.toString());
			obj.add(STATE);
		}
		obj.add(COMMON);
		if (obj instanceof RevCommit)
			((RevCommit) obj).carry(COMMON);
	}

	private void receivePack(final ProgressMonitor monitor,
			OutputStream outputStream) throws IOException {
		onReceivePack();
		InputStream input = in;
		if (sideband)
			input = new SideBandInputStream(input, monitor, getMessageWriter(),
					outputStream);

		try (ObjectInserter ins = local.newObjectInserter()) {
			PackParser parser = ins.newPackParser(input);
			parser.setAllowThin(thinPack);
			parser.setObjectChecker(transport.getObjectChecker());
			parser.setLockMessage(lockMessage);
			packLock = parser.parse(monitor);
			ins.flush();
		}
	}

	/**
	 * Notification event delivered just before the pack is received from the
	 * network. This event can be used by RPC such as {@link TransportHttp} to
	 * disable its request magic and ensure the pack stream is read correctly.
	 *
	 * @since 2.0
	 */
	protected void onReceivePack() {
		// By default do nothing for TCP based protocols.
	}

	private static class CancelledException extends Exception {
		private static final long serialVersionUID = 1L;
	}
}
