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

@SuppressWarnings("serial")
public class BadLimitsException extends Exception {
	private Pair<Integer, Integer> ip;

	BadLimitsException(int min, int max) {
		ip = Pair.of(min, max);
	}

	@Override
	public String toString() {
		return "[" + (new Integer(ip.getLeft())).toString() + "," + (new Integer(ip.getRight())).toString() + "]";
	}
}
