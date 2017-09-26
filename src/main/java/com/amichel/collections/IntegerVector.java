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
import java.util.Vector;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

/**
 * A vector of integer values
 */
@SuppressWarnings("serial")
public class IntegerVector extends Vector<Integer> {
	/**
	 * Default constructor. Constructs an empty IntegerVector
	 */
	public IntegerVector() {
		super();
	}

	/**
	 * Constructs an IntegerVector object from an IntegerSet, by copying all the
	 * values in the set into the vector
	 *
	 * @param set
	 *            The set containing the source for the integer values
	 */
	public IntegerVector(IntegerSet set) {
		Validate.notNull(set);
		for (Integer element : set) {
			addElement(element);
		}
	}

	/**
	 * Dumps the string representation of the IntegerSet to the output stream
	 *
	 * @param os
	 *            The output stream
	 * @exception IOException
	 */
	public void dump(OutputStream os) throws IOException {
		os.write(toString().getBytes());
	}

	/**
	 * Generates a string representation of the IntegerVector object. The format is
	 * "{1,2,3}".
	 *
	 * @return The string representation of the vector
	 */
	@Override
	public String toString() {
		return "{" + StringUtils.join(this, ",") + "}";
	}
}
