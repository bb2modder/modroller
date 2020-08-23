/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import org.eclipse.jgit.internal.JGitText;

import java.text.MessageFormat;
import java.util.Collection;

/**
 * Miscellaneous string comparison utility methods.
 */
public final class StringUtils {
	private static final char[] LC;

	static {
		LC = new char['Z' + 1];
		for (char c = 0; c < LC.length; c++)
			LC[c] = c;
		for (char c = 'A'; c <= 'Z'; c++)
			LC[c] = (char) ('a' + (c - 'A'));
	}

	/**
	 * Convert the input to lowercase.
	 * <p>
	 * This method does not honor the JVM locale, but instead always behaves as
	 * though it is in the US-ASCII locale. Only characters in the range 'A'
	 * through 'Z' are converted. All other characters are left as-is, even if
	 * they otherwise would have a lowercase character equivalent.
	 *
	 * @param c
	 *            the input character.
	 * @return lowercase version of the input.
	 */
	public static char toLowerCase(char c) {
		return c <= 'Z' ? LC[c] : c;
	}

	/**
	 * Convert the input string to lower case, according to the "C" locale.
	 * <p>
	 * This method does not honor the JVM locale, but instead always behaves as
	 * though it is in the US-ASCII locale. Only characters in the range 'A'
	 * through 'Z' are converted, all other characters are left as-is, even if
	 * they otherwise would have a lowercase character equivalent.
	 *
	 * @param in
	 *            the input string. Must not be null.
	 * @return a copy of the input string, after converting characters in the
	 *         range 'A'..'Z' to 'a'..'z'.
	 */
	public static String toLowerCase(String in) {
		final StringBuilder r = new StringBuilder(in.length());
		for (int i = 0; i < in.length(); i++)
			r.append(toLowerCase(in.charAt(i)));
		return r.toString();
	}


	/**
	 * Borrowed from commons-lang <code>StringUtils.capitalize()</code> method.
	 *
	 * <p>
	 * Capitalizes a String changing the first letter to title case as per
	 * {@link Character#toTitleCase(char)}. No other letters are
	 * changed.
	 * </p>
	 * <p>
	 * A <code>null</code> input String returns <code>null</code>.
	 * </p>
	 *
	 * @param str
	 *            the String to capitalize, may be null
	 * @return the capitalized String, <code>null</code> if null String input
	 * @since 4.0
	 */
	public static String capitalize(String str) {
		int strLen;
		if (str == null || (strLen = str.length()) == 0) {
			return str;
		}
		return new StringBuilder(strLen)
				.append(Character.toTitleCase(str.charAt(0)))
				.append(str.substring(1)).toString();
	}

	/**
	 * Test if two strings are equal, ignoring case.
	 * <p>
	 * This method does not honor the JVM locale, but instead always behaves as
	 * though it is in the US-ASCII locale.
	 *
	 * @param a
	 *            first string to compare.
	 * @param b
	 *            second string to compare.
	 * @return true if a equals b
	 */
	public static boolean equalsIgnoreCase(String a, String b) {
		if (References.isSameObject(a, b)) {
			return true;
		}
		if (a.length() != b.length())
			return false;
		for (int i = 0; i < a.length(); i++) {
			if (toLowerCase(a.charAt(i)) != toLowerCase(b.charAt(i)))
				return false;
		}
		return true;
	}

	/**
	 * Compare two strings, ignoring case.
	 * <p>
	 * This method does not honor the JVM locale, but instead always behaves as
	 * though it is in the US-ASCII locale.
	 *
	 * @param a
	 *            first string to compare.
	 * @param b
	 *            second string to compare.
	 * @since 2.0
	 * @return an int.
	 */
	public static int compareIgnoreCase(String a, String b) {
		for (int i = 0; i < a.length() && i < b.length(); i++) {
			int d = toLowerCase(a.charAt(i)) - toLowerCase(b.charAt(i));
			if (d != 0)
				return d;
		}
		return a.length() - b.length();
	}

	/**
	 * Compare two strings, honoring case.
	 * <p>
	 * This method does not honor the JVM locale, but instead always behaves as
	 * though it is in the US-ASCII locale.
	 *
	 * @param a
	 *            first string to compare.
	 * @param b
	 *            second string to compare.
	 * @since 2.0
	 * @return an int.
	 */
	public static int compareWithCase(String a, String b) {
		for (int i = 0; i < a.length() && i < b.length(); i++) {
			int d = a.charAt(i) - b.charAt(i);
			if (d != 0)
				return d;
		}
		return a.length() - b.length();
	}

	/**
	 * Parse a string as a standard Git boolean value. See
	 * {@link #toBooleanOrNull(String)}.
	 *
	 * @param stringValue
	 *            the string to parse.
	 * @return the boolean interpretation of {@code value}.
	 * @throws IllegalArgumentException
	 *             if {@code value} is not recognized as one of the standard
	 *             boolean names.
	 */
	public static boolean toBoolean(String stringValue) {
		if (stringValue == null)
			throw new NullPointerException(JGitText.get().expectedBooleanStringValue);

		final Boolean bool = toBooleanOrNull(stringValue);
		if (bool == null)
			throw new IllegalArgumentException(MessageFormat.format(JGitText.get().notABoolean, stringValue));

		return bool.booleanValue();
	}

	/**
	 * Parse a string as a standard Git boolean value.
	 * <p>
	 * The terms {@code yes}, {@code true}, {@code 1}, {@code on} can all be
	 * used to mean {@code true}.
	 * <p>
	 * The terms {@code no}, {@code false}, {@code 0}, {@code off} can all be
	 * used to mean {@code false}.
	 * <p>
	 * Comparisons ignore case, via {@link #equalsIgnoreCase(String, String)}.
	 *
	 * @param stringValue
	 *            the string to parse.
	 * @return the boolean interpretation of {@code value} or null in case the
	 *         string does not represent a boolean value
	 */
	public static Boolean toBooleanOrNull(String stringValue) {
		if (stringValue == null)
			return null;

		if (equalsIgnoreCase("yes", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("true", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("1", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("on", stringValue)) //$NON-NLS-1$
			return Boolean.TRUE;
		else if (equalsIgnoreCase("no", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("false", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("0", stringValue) //$NON-NLS-1$
				|| equalsIgnoreCase("off", stringValue)) //$NON-NLS-1$
			return Boolean.FALSE;
		else
			return null;
	}

	/**
	 * Join a collection of Strings together using the specified separator.
	 *
	 * @param parts
	 *            Strings to join
	 * @param separator
	 *            used to join
	 * @return a String with all the joined parts
	 */
	public static String join(Collection<String> parts, String separator) {
		return join(parts, separator, separator);
	}

	/**
	 * Join a collection of Strings together using the specified separator and a
	 * lastSeparator which is used for joining the second last and the last
	 * part.
	 *
	 * @param parts
	 *            Strings to join
	 * @param separator
	 *            separator used to join all but the two last elements
	 * @param lastSeparator
	 *            separator to use for joining the last two elements
	 * @return a String with all the joined parts
	 */
	public static String join(Collection<String> parts, String separator,
			String lastSeparator) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		int lastIndex = parts.size() - 1;
		for (String part : parts) {
			sb.append(part);
			if (i == lastIndex - 1) {
				sb.append(lastSeparator);
			} else if (i != lastIndex) {
				sb.append(separator);
			}
			i++;
		}
		return sb.toString();
	}

	private StringUtils() {
		// Do not create instances
	}

	/**
	 * Test if a string is empty or null.
	 *
	 * @param stringValue
	 *            the string to check
	 * @return <code>true</code> if the string is <code>null</code> or empty
	 */
	public static boolean isEmptyOrNull(String stringValue) {
		return stringValue == null || stringValue.length() == 0;
	}

	/**
	 * Replace CRLF, CR or LF with a single space.
	 *
	 * @param in
	 *            A string with line breaks
	 * @return in without line breaks
	 * @since 3.1
	 */
	public static String replaceLineBreaksWithSpace(String in) {
		char[] buf = new char[in.length()];
		int o = 0;
		for (int i = 0; i < buf.length; ++i) {
			char ch = in.charAt(i);
			switch (ch) {
			case '\r':
				if (i + 1 < buf.length && in.charAt(i + 1) == '\n') {
					buf[o++] = ' ';
					++i;
				} else
					buf[o++] = ' ';
				break;
			case '\n':
				buf[o++] = ' ';
				break;
			default:
				buf[o++] = ch;
				break;
			}
		}
		return new String(buf, 0, o);
	}
}
