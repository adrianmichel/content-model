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

/***********************************************
 * exception thrown if the content model is ambiguous
 **********************************************/
@SuppressWarnings("serial")
public class AmbiguousContentModelException extends Exception {
	private String name = null;
	private String message = null;

	public AmbiguousContentModelException(String _name, String _message) {
		name = _name;
		message = _message;
	}

	public String getName() {
		return name;
	}

	@Override
	public String getMessage() {
		return message;
	}
}
