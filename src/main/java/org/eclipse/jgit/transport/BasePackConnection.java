/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

import org.eclipse.jgit.errors.*;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.io.InterruptTimer;
import org.eclipse.jgit.util.io.TimeoutInputStream;
import org.eclipse.jgit.util.io.TimeoutOutputStream;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.eclipse.jgit.transport.GitProtocolConstants.OPTION_AGENT;

/**
 * Base helper class for pack-based operations implementations. Provides partial
 * implementation of pack-protocol - refs advertising and capabilities support,
 * and some other helper methods.
 *
 * @see BasePackFetchConnection
 * @see BasePackPushConnection
 */
abstract class BasePackConnection extends BaseConnection {

	/** The repository this transport fetches into, or pushes out of. */
	protected final Repository local;

	/** Remote repository location. */
	protected final URIish uri;

	/** A transport connected to {@link #uri}. */
	protected final Transport transport;

	/** Low-level input stream, if a timeout was configured. */
	protected TimeoutInputStream timeoutIn;

	/** Low-level output stream, if a timeout was configured. */
	protected TimeoutOutputStream timeoutOut;

	/** Timer to manage {@link #timeoutIn} and {@link #timeoutOut}. */
	private InterruptTimer myTimer;

	/** Input stream reading from the remote. */
	protected InputStream in;

	/** Output stream sending to the remote. */
	protected OutputStream out;

	/** Packet line decoder around {@link #in}. */
	protected PacketLineIn pckIn;

	/** Packet line encoder around {@link #out}. */
	protected PacketLineOut pckOut;

	/** Send {@link PacketLineOut#end()} before closing {@link #out}? */
	protected boolean outNeedsEnd;

	/** True if this is a stateless RPC connection. */
	protected boolean statelessRPC;

	/** Capability tokens advertised by the remote side. */
	private final Set<String> remoteCapablities = new HashSet<>();

	/** Extra objects the remote has, but which aren't offered as refs. */
	protected final Set<ObjectId> additionalHaves = new HashSet<>();

	BasePackConnection(PackTransport packTransport) {
		transport = (Transport) packTransport;
		local = transport.local;
		uri = transport.uri;
	}

	/**
	 * Configure this connection with the directional pipes.
	 *
	 * @param myIn
	 *            input stream to receive data from the peer. Caller must ensure
	 *            the input is buffered, otherwise read performance may suffer.
	 * @param myOut
	 *            output stream to transmit data to the peer. Caller must ensure
	 *            the output is buffered, otherwise write performance may
	 *            suffer.
	 */
	protected final void init(InputStream myIn, OutputStream myOut) {
		final int timeout = transport.getTimeout();
		if (timeout > 0) {
			final Thread caller = Thread.currentThread();
			if (myTimer == null) {
				myTimer = new InterruptTimer(caller.getName() + "-Timer"); //$NON-NLS-1$
			}
			timeoutIn = new TimeoutInputStream(myIn, myTimer);
			timeoutOut = new TimeoutOutputStream(myOut, myTimer);
			timeoutIn.setTimeout(timeout * 1000);
			timeoutOut.setTimeout(timeout * 1000);
			myIn = timeoutIn;
			myOut = timeoutOut;
		}

		in = myIn;
		out = myOut;

		pckIn = new PacketLineIn(in);
		pckOut = new PacketLineOut(out);
		outNeedsEnd = true;
	}

	/**
	 * Reads the advertised references through the initialized stream.
	 * <p>
	 * Subclass implementations may call this method only after setting up the
	 * input and output streams with {@link #init(InputStream, OutputStream)}.
	 * <p>
	 * If any errors occur, this connection is automatically closed by invoking
	 * {@link #close()} and the exception is wrapped (if necessary) and thrown
	 * as a {@link TransportException}.
	 *
	 * @throws TransportException
	 *             the reference list could not be scanned.
	 */
	protected void readAdvertisedRefs() throws TransportException {
		try {
			readAdvertisedRefsImpl();
		} catch (TransportException err) {
			close();
			throw err;
		} catch (IOException | RuntimeException err) {
			close();
			throw new TransportException(err.getMessage(), err);
		}
	}

	private void readAdvertisedRefsImpl() throws IOException {
		final LinkedHashMap<String, Ref> avail = new LinkedHashMap<>();
		for (;;) {
			String line;

			try {
				line = pckIn.readString();
			} catch (EOFException eof) {
				if (avail.isEmpty())
					throw noRepository();
				throw eof;
			}
			if (PacketLineIn.isEnd(line))
				break;

			if (line.startsWith("ERR ")) { //$NON-NLS-1$
				// This is a customized remote service error.
				// Users should be informed about it.
				throw new RemoteRepositoryException(uri, line.substring(4));
			}

			if (avail.isEmpty()) {
				final int nul = line.indexOf('\0');
				if (nul >= 0) {
					// The first line (if any) may contain "hidden"
					// capability values after a NUL byte.
					remoteCapablities.addAll(
							Arrays.asList(line.substring(nul + 1).split(" "))); //$NON-NLS-1$
					line = line.substring(0, nul);
				}
			}

			// Expecting to get a line in the form "sha1 refname"
			if (line.length() < 41 || line.charAt(40) != ' ') {
				throw invalidRefAdvertisementLine(line);
			}
			String name = line.substring(41, line.length());
			if (avail.isEmpty() && name.equals("capabilities^{}")) { //$NON-NLS-1$
				// special line from git-receive-pack to show
				// capabilities when there are no refs to advertise
				continue;
			}

			final ObjectId id;
			try {
				id  = ObjectId.fromString(line.substring(0, 40));
			} catch (InvalidObjectIdException e) {
				PackProtocolException ppe = invalidRefAdvertisementLine(line);
				ppe.initCause(e);
				throw ppe;
			}
			if (name.equals(".have")) { //$NON-NLS-1$
				additionalHaves.add(id);
			} else if (name.endsWith("^{}")) { //$NON-NLS-1$
				name = name.substring(0, name.length() - 3);
				final Ref prior = avail.get(name);
				if (prior == null)
					throw new PackProtocolException(uri, MessageFormat.format(
							JGitText.get().advertisementCameBefore, name, name));

				if (prior.getPeeledObjectId() != null)
					throw duplicateAdvertisement(name + "^{}"); //$NON-NLS-1$

				avail.put(name, new ObjectIdRef.PeeledTag(
						Ref.Storage.NETWORK, name, prior.getObjectId(), id));
			} else {
				final Ref prior = avail.put(name, new ObjectIdRef.PeeledNonTag(
						Ref.Storage.NETWORK, name, id));
				if (prior != null)
					throw duplicateAdvertisement(name);
			}
		}
		available(avail);
	}

	/**
	 * Create an exception to indicate problems finding a remote repository. The
	 * caller is expected to throw the returned exception.
	 *
	 * Subclasses may override this method to provide better diagnostics.
	 *
	 * @return a TransportException saying a repository cannot be found and
	 *         possibly why.
	 */
	protected TransportException noRepository() {
		return new NoRemoteRepositoryException(uri, JGitText.get().notFound);
	}

	/**
	 * Whether this option is supported
	 *
	 * @param option
	 *            option string
	 * @return whether this option is supported
	 */
	protected boolean isCapableOf(String option) {
		return remoteCapablities.contains(option);
	}

	/**
	 * Request capability
	 *
	 * @param b
	 *            buffer
	 * @param option
	 *            option we want
	 * @return {@code true} if the requested option is supported
	 */
	protected boolean wantCapability(StringBuilder b, String option) {
		if (!isCapableOf(option))
			return false;
		b.append(' ');
		b.append(option);
		return true;
	}

	/**
	 * Add user agent capability
	 *
	 * @param b
	 *            a {@link StringBuilder} object.
	 */
	protected void addUserAgentCapability(StringBuilder b) {
		String a = UserAgent.get();
		if (a != null && UserAgent.hasAgent(remoteCapablities)) {
			b.append(' ').append(OPTION_AGENT).append('=').append(a);
		}
	}

	/** {@inheritDoc} */
	@Override
	public String getPeerUserAgent() {
		return UserAgent.getAgent(remoteCapablities, super.getPeerUserAgent());
	}

	private PackProtocolException duplicateAdvertisement(String name) {
		return new PackProtocolException(uri, MessageFormat.format(JGitText.get().duplicateAdvertisementsOf, name));
	}

	private PackProtocolException invalidRefAdvertisementLine(String line) {
		return new PackProtocolException(uri, MessageFormat.format(JGitText.get().invalidRefAdvertisementLine, line));
	}

	/** {@inheritDoc} */
	@Override
	public void close() {
		if (out != null) {
			try {
				if (outNeedsEnd) {
					outNeedsEnd = false;
					pckOut.end();
				}
				out.close();
			} catch (IOException err) {
				// Ignore any close errors.
			} finally {
				out = null;
				pckOut = null;
			}
		}

		if (in != null) {
			try {
				in.close();
			} catch (IOException err) {
				// Ignore any close errors.
			} finally {
				in = null;
				pckIn = null;
			}
		}

		if (myTimer != null) {
			try {
				myTimer.terminate();
			} finally {
				myTimer = null;
				timeoutIn = null;
				timeoutOut = null;
			}
		}
	}

	/**
	 * Tell the peer we are disconnecting, if it cares to know.
	 */
	protected void endOut() {
		if (outNeedsEnd && out != null) {
			try {
				outNeedsEnd = false;
				pckOut.end();
			} catch (IOException e) {
				try {
					out.close();
				} catch (IOException err) {
					// Ignore any close errors.
				} finally {
					out = null;
					pckOut = null;
				}
			}
		}
	}
}
