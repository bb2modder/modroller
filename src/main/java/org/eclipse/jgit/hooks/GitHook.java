/*
 * Copyright (C) 2015 Obeo. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.hooks;

import org.eclipse.jgit.api.errors.AbortedByHookException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.ProcessResult;
import org.eclipse.jgit.util.io.TeeOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.Callable;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Git can fire off custom scripts when certain important actions occur. These
 * custom scripts are called "hooks". There are two groups of hooks: client-side
 * (that run on local operations such as committing and merging), and
 * server-side (that run on network operations such as receiving pushed
 * commits). This is the abstract super-class of the different hook
 * implementations in JGit.
 *
 * @param <T>
 *            the return type which is expected from {@link #call()}
 * @see <a href="http://git-scm.com/book/en/v2/Customizing-Git-Git-Hooks">Git
 *      Hooks on the git-scm official site</a>
 * @since 4.0
 */
abstract class GitHook<T> implements Callable<T> {

	private final Repository repo;

	/**
	 * The output stream to be used by the hook.
	 */
	protected final PrintStream outputStream;

	/**
	 * The error stream to be used by the hook.
	 */
	protected final PrintStream errorStream;

	/**
	 * Constructor for GitHook.
	 * <p>
	 * This constructor will use stderr for the error stream.
	 * </p>
	 *
	 * @param repo
	 *            a {@link Repository} object.
	 * @param outputStream
	 *            The output stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.out}.
	 */
	protected GitHook(Repository repo, PrintStream outputStream) {
		this(repo, outputStream, null);
	}

	/**
	 * Constructor for GitHook
	 *
	 * @param repo
	 *            a {@link Repository} object.
	 * @param outputStream
	 *            The output stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.out}.
	 * @param errorStream
	 *            The error stream the hook must use. {@code null} is allowed,
	 *            in which case the hook will use {@code System.err}.
	 */
	protected GitHook(Repository repo, PrintStream outputStream,
			PrintStream errorStream) {
		this.repo = repo;
		this.outputStream = outputStream;
		this.errorStream = errorStream;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Run the hook.
	 */
	@Override
	public abstract T call() throws IOException, AbortedByHookException;

	/**
	 * Get name of the hook
	 *
	 * @return The name of the hook, which must not be {@code null}.
	 */
	public abstract String getHookName();

	/**
	 * Get the repository
	 *
	 * @return The repository.
	 */
	protected Repository getRepository() {
		return repo;
	}

	/**
	 * Override this method when needed to provide relevant parameters to the
	 * underlying hook script. The default implementation returns an empty
	 * array.
	 *
	 * @return The parameters the hook receives.
	 */
	protected String[] getParameters() {
		return new String[0];
	}

	/**
	 * Override to provide relevant arguments via stdin to the underlying hook
	 * script. The default implementation returns {@code null}.
	 *
	 * @return The parameters the hook receives.
	 */
	protected String getStdinArgs() {
		return null;
	}

	/**
	 * Get output stream
	 *
	 * @return The output stream the hook must use. Never {@code null},
	 *         {@code System.out} is returned by default.
	 */
	protected PrintStream getOutputStream() {
		return outputStream == null ? System.out : outputStream;
	}

	/**
	 * Get error stream
	 *
	 * @return The error stream the hook must use. Never {@code null},
	 *         {@code System.err} is returned by default.
	 */
	protected PrintStream getErrorStream() {
		return errorStream == null ? System.err : errorStream;
	}

	/**
	 * Runs the hook, without performing any validity checks.
	 *
	 * @throws AbortedByHookException
	 *             If the underlying hook script exited with non-zero.
	 */
	protected void doRun() throws AbortedByHookException {
		final ByteArrayOutputStream errorByteArray = new ByteArrayOutputStream();
		final TeeOutputStream stderrStream = new TeeOutputStream(errorByteArray,
				getErrorStream());
		PrintStream hookErrRedirect = null;
		try {
			hookErrRedirect = new PrintStream(stderrStream, false,
					UTF_8.name());
		} catch (UnsupportedEncodingException e) {
			// UTF-8 is guaranteed to be available
		}
		Repository repository = getRepository();
		FS fs = repository.getFS();
		if (fs == null) {
			fs = FS.DETECTED;
		}
		ProcessResult result = fs.runHookIfPresent(repository, getHookName(),
				getParameters(), getOutputStream(), hookErrRedirect,
				getStdinArgs());
		if (result.isExecutedWithError()) {
			throw new AbortedByHookException(
					new String(errorByteArray.toByteArray(), UTF_8),
					getHookName(), result.getExitCode());
		}
	}

	/**
	 * Check whether a 'native' (i.e. script) hook is installed in the
	 * repository.
	 *
	 * @return whether a native hook script is installed in the repository.
	 * @since 4.11
	 */
	public boolean isNativeHookPresent() {
		FS fs = getRepository().getFS();
		if (fs == null) {
			fs = FS.DETECTED;
		}
		return fs.findHook(getRepository(), getHookName()) != null;
	}

}
