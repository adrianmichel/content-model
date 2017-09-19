/*
 * Copyright (c) 2017, Adrian Michel
 * http://www.amichel.com
 * 
 * This software is released under the 3-Clause BSD License
 * 
 * The complete terms can be found in the attached LICENSE file 
 * or at https://opensource.org/licenses/BSD-3-Clause 
*/

package com.amichel.contentmodel;

import org.apache.commons.lang3.tuple.Pair;

class Limits {
	public final static int INFINITY = -1;

	private Pair<Integer, Integer> limits;

	Limits(int min, int max) throws BadLimitsException {
		limits = Pair.of(min, max);
		if (min < 0 || max < INFINITY || max >= 0 && min > max)
			throw new BadLimitsException(min, max);
	}

	private Limits(Pair<Integer, Integer> ip) {
		limits = ip;
	}

	public Limits clone() {
		return new Limits(Pair.of(limits.getLeft(), limits.getRight()));
	}

	public int min() {
		return limits.getLeft();
	}

	public int max() {
		return limits.getRight();
	}

	public boolean checkMin(int count) {
		return count >= min();
	}

	public boolean checkMax(int count) {
		return max() == INFINITY || count <= max();
	}

	public String toString() {
		String min = (new Integer(min())).toString();
		String max = max() == -1 ? "*" : (new Integer(max())).toString();
		return "[" + min + "," + max + "]";
	}

	public void dump() {
		ModelWriter.print(toString());
	}
}
