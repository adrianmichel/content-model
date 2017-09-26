/*
 * Copyright (c) 2017, Adrian Michel
 * http://www.amichel.com
 * 
 * This software is released under the 3-Clause BSD License
 * 
 * The complete terms can be found in the attached LICENSE file 
 * or at https://opensource.org/licenses/BSD-3-Clause 
*/

package com.amichel.collections;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

/**
 * IntegerSet class - models a set of unique integer values.
 *
 * The hash value is the sum of all elements, which allows for fast comparison
 * in case the two hash values are different, the sets are different.
 *
 * Note for syntax tree class: when the tree is brought to the Star Normal Form,
 * the integer set used by model will be implemented as lists, because all sets
 * manipulated are disjoint in this case and so the reunion operation can be
 * modeled as a simple concatenation of two lists (this is to optimize the DFA
 * generation algorithm)
 */
@SuppressWarnings("serial")
public class IntegerSet extends HashSet<Integer> {

	/**
	 * Default constructor
	 */
	public IntegerSet() {
	}

	/**
	 * Constructor that sets the first integer value in the set
	 *
	 * @param n
	 *            The integer value to be added to the set
	 */
	public IntegerSet(int n) {
		this();
		add(new Integer(n));
	}

	/**
	 * Does the reunion of an integer set with the current integer sets. All the new
	 * values are added to the current integer set, which holds the reunion. In case
	 * of duplicate values, only one of them is added to the new set.
	 *
	 * @param set
	 *            The set to do the reunion with
	 * @return A reference to the current integer set
	 */
	public IntegerSet reunion(IntegerSet set) {
		Validate.notNull(set);

		for (Integer element : set) {
			// only add to the hashcode the new values
			add(element);
		}
		return this;
	}

	/**
	 * Indicates whether two sets have any common values.
	 *
	 * @param set
	 *            The set to compare with
	 * @return true if the two sets have common values, false otherwise
	 */
	public boolean intersection(Set<Integer> set) {
		Validate.notNull(set);

		// Set intersection = new Set();
		for (Integer element : set) {
			if (contains(element))
				return true;
		}
		return false;
	}

	/**
	 * Dumps the contents of the set to an output stream
	 *
	 * @param os
	 *            the output stream
	 * @exception IOException
	 */
	public void dump(OutputStream os) throws IOException {
		// TODO: replace all ModelWriter... with C1 statements
		os.write(toString().getBytes());
	}

	/**
	 * Creates a string representation of the IntegerSet object and it has the
	 * following format: "{1,2,3}".
	 *
	 * @return The string representation of the IntegerSet object
	 */
	@Override
	public String toString() {
		return "{" + StringUtils.join(this, ",") + "}";
	}
}
