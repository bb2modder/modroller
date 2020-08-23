/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FS.FileStoreAttributes;

import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.eclipse.jgit.util.FS.FileStoreAttributes.FALLBACK_FILESTORE_ATTRIBUTES;
import static org.eclipse.jgit.util.FS.FileStoreAttributes.FALLBACK_TIMESTAMP_RESOLUTION;

/**
 * Caches when a file was last read, making it possible to detect future edits.
 * <p>
 * This object tracks the last modified time of a file. Later during an
 * invocation of {@link #isModified(File)} the object will return true if the
 * file may have been modified and should be re-read from disk.
 * <p>
 * A snapshot does not "live update" when the underlying filesystem changes.
 * Callers must poll for updates by periodically invoking
 * {@link #isModified(File)}.
 * <p>
 * To work around the "racy git" problem (where a file may be modified multiple
 * times within the granularity of the filesystem modification clock) this class
 * may return true from isModified(File) if the last modification time of the
 * file is less than 3 seconds ago.
 */
public class FileSnapshot {
	/**
	 * An unknown file size.
	 *
	 * This value is used when a comparison needs to happen purely on the lastUpdate.
	 */
	public static final long UNKNOWN_SIZE = -1;

	private static final Instant UNKNOWN_TIME = Instant.ofEpochMilli(-1);

	private static final Object MISSING_FILEKEY = new Object();

	private static final DateTimeFormatter dateFmt = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn") //$NON-NLS-1$
			.withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

	/**
	 * A FileSnapshot that is considered to always be modified.
	 * <p>
	 * This instance is useful for application code that wants to lazily read a
	 * file, but only after {@link #isModified(File)} gets invoked. The returned
	 * snapshot contains only invalid status information.
	 */
	public static final FileSnapshot DIRTY = new FileSnapshot(UNKNOWN_TIME,
			UNKNOWN_TIME, UNKNOWN_SIZE, Duration.ZERO, MISSING_FILEKEY);

	/**
	 * A FileSnapshot that is clean if the file does not exist.
	 * <p>
	 * This instance is useful if the application wants to consider a missing
	 * file to be clean. {@link #isModified(File)} will return false if the file
	 * path does not exist.
	 */
	public static final FileSnapshot MISSING_FILE = new FileSnapshot(
			Instant.EPOCH, Instant.EPOCH, 0, Duration.ZERO, MISSING_FILEKEY) {
		@Override
		public boolean isModified(File path) {
			return FS.DETECTED.exists(path);
		}
	};

	/**
	 * Record a snapshot for a specific file path.
	 * <p>
	 * This method should be invoked before the file is accessed.
	 *
	 * @param path
	 *            the path to later remember. The path's current status
	 *            information is saved.
	 * @return the snapshot.
	 */
	public static FileSnapshot save(File path) {
		return new FileSnapshot(path);
	}

	/**
	 * Record a snapshot for a specific file path without using config file to
	 * get filesystem timestamp resolution.
	 * <p>
	 * This method should be invoked before the file is accessed. It is used by
	 * FileBasedConfig to avoid endless recursion.
	 *
	 * @param path
	 *            the path to later remember. The path's current status
	 *            information is saved.
	 * @return the snapshot.
	 */
	public static FileSnapshot saveNoConfig(File path) {
		return new FileSnapshot(path, false);
	}

	private static Object getFileKey(BasicFileAttributes fileAttributes) {
		Object fileKey = fileAttributes.fileKey();
		return fileKey == null ? MISSING_FILEKEY : fileKey;
	}

	/**
	 * Record a snapshot for a file for which the last modification time is
	 * already known.
	 * <p>
	 * This method should be invoked before the file is accessed.
	 * <p>
	 * Note that this method cannot rely on measuring file timestamp resolution
	 * to avoid racy git issues caused by finite file timestamp resolution since
	 * it's unknown in which filesystem the file is located. Hence the worst
	 * case fallback for timestamp resolution is used.
	 *
	 * @param modified
	 *            the last modification time of the file
	 * @return the snapshot.
	 * @deprecated use {@link #save(Instant)} instead.
	 */
	@Deprecated
	public static FileSnapshot save(long modified) {
		final Instant read = Instant.now();
		return new FileSnapshot(read, Instant.ofEpochMilli(modified),
				UNKNOWN_SIZE, FALLBACK_TIMESTAMP_RESOLUTION, MISSING_FILEKEY);
	}

	/**
	 * Record a snapshot for a file for which the last modification time is
	 * already known.
	 * <p>
	 * This method should be invoked before the file is accessed.
	 * <p>
	 * Note that this method cannot rely on measuring file timestamp resolution
	 * to avoid racy git issues caused by finite file timestamp resolution since
	 * it's unknown in which filesystem the file is located. Hence the worst
	 * case fallback for timestamp resolution is used.
	 *
	 * @param modified
	 *            the last modification time of the file
	 * @return the snapshot.
	 */
	public static FileSnapshot save(Instant modified) {
		final Instant read = Instant.now();
		return new FileSnapshot(read, modified, UNKNOWN_SIZE,
				FALLBACK_TIMESTAMP_RESOLUTION, MISSING_FILEKEY);
	}

	/** Last observed modification time of the path. */
	private final Instant lastModified;

	/** Last wall-clock time the path was read. */
	private volatile Instant lastRead;

	/** True once {@link #lastRead} is far later than {@link #lastModified}. */
	private boolean cannotBeRacilyClean;

	/** Underlying file-system size in bytes.
	 *
	 * When set to {@link #UNKNOWN_SIZE} the size is not considered for modification checks. */
	private final long size;

	/** measured FileStore attributes */
	private FileStoreAttributes fileStoreAttributeCache;

	/**
	 * Object that uniquely identifies the given file, or {@code
	 * null} if a file key is not available
	 */
	private final Object fileKey;

	private final File file;

	/**
	 * Record a snapshot for a specific file path.
	 * <p>
	 * This method should be invoked before the file is accessed.
	 *
	 * @param file
	 *            the path to remember meta data for. The path's current status
	 *            information is saved.
	 */
	protected FileSnapshot(File file) {
		this(file, true);
	}

	/**
	 * Record a snapshot for a specific file path.
	 * <p>
	 * This method should be invoked before the file is accessed.
	 *
	 * @param file
	 *            the path to remember meta data for. The path's current status
	 *            information is saved.
	 * @param useConfig
	 *            if {@code true} read filesystem time resolution from
	 *            configuration file otherwise use fallback resolution
	 */
	protected FileSnapshot(File file, boolean useConfig) {
		this.file = file;
		this.lastRead = Instant.now();
		this.fileStoreAttributeCache = useConfig
				? FS.getFileStoreAttributes(file.toPath().getParent())
				: FALLBACK_FILESTORE_ATTRIBUTES;
		BasicFileAttributes fileAttributes = null;
		try {
			fileAttributes = FS.DETECTED.fileAttributes(file);
		} catch (IOException e) {
			this.lastModified = Instant.ofEpochMilli(file.lastModified());
			this.size = file.length();
			this.fileKey = MISSING_FILEKEY;
			return;
		}
		this.lastModified = fileAttributes.lastModifiedTime().toInstant();
		this.size = fileAttributes.size();
		this.fileKey = getFileKey(fileAttributes);
	}

	private boolean sizeChanged;

	private boolean fileKeyChanged;

	private boolean lastModifiedChanged;

	private boolean wasRacyClean;

	private long delta;

	private long racyThreshold;

	private FileSnapshot(Instant read, Instant modified, long size,
			@NonNull Duration fsTimestampResolution, @NonNull Object fileKey) {
		this.file = null;
		this.lastRead = read;
		this.lastModified = modified;
		this.fileStoreAttributeCache = new FileStoreAttributes(
				fsTimestampResolution);
		this.size = size;
		this.fileKey = fileKey;
	}

	/**
	 * Get time of last snapshot update
	 *
	 * @return time of last snapshot update
	 * @deprecated use {@link #lastModifiedInstant()} instead
	 */
	@Deprecated
	public long lastModified() {
		return lastModified.toEpochMilli();
	}

	/**
	 * Get time of last snapshot update
	 *
	 * @return time of last snapshot update
	 */
	public Instant lastModifiedInstant() {
		return lastModified;
	}

	/**
	 * @return file size in bytes of last snapshot update
	 */
	public long size() {
		return size;
	}

	/**
	 * Check if the path may have been modified since the snapshot was saved.
	 *
	 * @param path
	 *            the path the snapshot describes.
	 * @return true if the path needs to be read again.
	 */
	public boolean isModified(File path) {
		Instant currLastModified;
		long currSize;
		Object currFileKey;
		try {
			BasicFileAttributes fileAttributes = FS.DETECTED.fileAttributes(path);
			currLastModified = fileAttributes.lastModifiedTime().toInstant();
			currSize = fileAttributes.size();
			currFileKey = getFileKey(fileAttributes);
		} catch (IOException e) {
			currLastModified = Instant.ofEpochMilli(path.lastModified());
			currSize = path.length();
			currFileKey = MISSING_FILEKEY;
		}
		sizeChanged = isSizeChanged(currSize);
		if (sizeChanged) {
			return true;
		}
		fileKeyChanged = isFileKeyChanged(currFileKey);
		if (fileKeyChanged) {
			return true;
		}
		lastModifiedChanged = isModified(currLastModified);
		if (lastModifiedChanged) {
			return true;
		}
		return false;
	}

	/**
	 * Update this snapshot when the content hasn't changed.
	 * <p>
	 * If the caller gets true from {@link #isModified(File)}, re-reads the
	 * content, discovers the content is identical, and
	 * {@link #equals(FileSnapshot)} is true, it can use
	 * {@link #setClean(FileSnapshot)} to make a future
	 * {@link #isModified(File)} return false. The logic goes something like
	 * this:
	 *
	 * <pre>
	 * if (snapshot.isModified(path)) {
	 *  FileSnapshot other = FileSnapshot.save(path);
	 *  Content newContent = ...;
	 *  if (oldContent.equals(newContent) &amp;&amp; snapshot.equals(other))
	 *      snapshot.setClean(other);
	 * }
	 * </pre>
	 *
	 * @param other
	 *            the other snapshot.
	 */
	public void setClean(FileSnapshot other) {
		final Instant now = other.lastRead;
		if (!isRacyClean(now)) {
			cannotBeRacilyClean = true;
		}
		lastRead = now;
	}

	/**
	 * Wait until this snapshot's file can't be racy anymore
	 *
	 * @throws InterruptedException
	 *             if sleep was interrupted
	 */
	public void waitUntilNotRacy() throws InterruptedException {
		long timestampResolution = fileStoreAttributeCache
				.getFsTimestampResolution().toNanos();
		while (isRacyClean(Instant.now())) {
			TimeUnit.NANOSECONDS.sleep(timestampResolution);
		}
	}

	/**
	 * Compare two snapshots to see if they cache the same information.
	 *
	 * @param other
	 *            the other snapshot.
	 * @return true if the two snapshots share the same information.
	 */
	@SuppressWarnings("NonOverridingEquals")
	public boolean equals(FileSnapshot other) {
		boolean sizeEq = size == UNKNOWN_SIZE || other.size == UNKNOWN_SIZE || size == other.size;
		return lastModified.equals(other.lastModified) && sizeEq
				&& Objects.equals(fileKey, other.fileKey);
	}

	/** {@inheritDoc} */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof FileSnapshot)) {
			return false;
		}
		FileSnapshot other = (FileSnapshot) obj;
		return equals(other);
	}

	/** {@inheritDoc} */
	@Override
	public int hashCode() {
		return Objects.hash(lastModified, Long.valueOf(size), fileKey);
	}

	/**
	 * @return {@code true} if FileSnapshot.isModified(File) found the file size
	 *         changed
	 */
	boolean wasSizeChanged() {
		return sizeChanged;
	}

	/**
	 * @return {@code true} if FileSnapshot.isModified(File) found the file key
	 *         changed
	 */
	boolean wasFileKeyChanged() {
		return fileKeyChanged;
	}

	/**
	 * @return {@code true} if FileSnapshot.isModified(File) found the file's
	 *         lastModified changed
	 */
	boolean wasLastModifiedChanged() {
		return lastModifiedChanged;
	}

	/**
	 * @return {@code true} if FileSnapshot.isModified(File) detected that
	 *         lastModified is racily clean
	 */
	boolean wasLastModifiedRacilyClean() {
		return wasRacyClean;
	}

	/**
	 * @return the delta in nanoseconds between lastModified and lastRead during
	 *         last racy check
	 */
	public long lastDelta() {
		return delta;
	}

	/**
	 * @return the racyLimitNanos threshold in nanoseconds during last racy
	 *         check
	 */
	public long lastRacyThreshold() {
		return racyThreshold;
	}

	/** {@inheritDoc} */
	@SuppressWarnings({ "nls", "ReferenceEquality" })
	@Override
	public String toString() {
		if (this == DIRTY) {
			return "DIRTY";
		}
		if (this == MISSING_FILE) {
			return "MISSING_FILE";
		}
		return "FileSnapshot[modified: " + dateFmt.format(lastModified)
				+ ", read: " + dateFmt.format(lastRead) + ", size:" + size
				+ ", fileKey: " + fileKey + "]";
	}

	private boolean isRacyClean(Instant read) {
		racyThreshold = getEffectiveRacyThreshold();
		delta = Duration.between(lastModified, read).toNanos();
		wasRacyClean = delta <= racyThreshold;
		return wasRacyClean;
	}

	private long getEffectiveRacyThreshold() {
		long timestampResolution = fileStoreAttributeCache
				.getFsTimestampResolution().toNanos();
		long minRacyInterval = fileStoreAttributeCache.getMinimalRacyInterval()
				.toNanos();
		long max = Math.max(timestampResolution, minRacyInterval);
		// safety margin: factor 2.5 below 100ms otherwise 1.25
		return max < 100_000_000L ? max * 5 / 2 : max * 5 / 4;
	}

	private boolean isModified(Instant currLastModified) {
		// Any difference indicates the path was modified.

		lastModifiedChanged = !lastModified.equals(currLastModified);
		if (lastModifiedChanged) {
			return true;
		}

		// We have already determined the last read was far enough
		// after the last modification that any new modifications
		// are certain to change the last modified time.
		if (cannotBeRacilyClean) {
			return false;
		}
		if (!isRacyClean(lastRead)) {
			// Our last read should have marked cannotBeRacilyClean,
			// but this thread may not have seen the change. The read
			// of the volatile field lastRead should have fixed that.
			return false;
		}

		// We last read this path too close to its last observed
		// modification time. We may have missed a modification.
		// Scan again, to ensure we still see the same state.
		return true;
	}

	private boolean isFileKeyChanged(Object currFileKey) {
		boolean changed = currFileKey != MISSING_FILEKEY
				&& !currFileKey.equals(fileKey);
		return changed;
	}

	private boolean isSizeChanged(long currSize) {
		boolean changed = (currSize != UNKNOWN_SIZE) && (currSize != size);
		return changed;
	}
}
