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

import java.util.Vector;

class LimitsVector extends Vector<Limits> {
	public void dump() {
		for (Limits limits : this) {
			limits.dump();
		}
	}

	Limits getLimits(int n) {
		return elementAt(n);
	}
}
