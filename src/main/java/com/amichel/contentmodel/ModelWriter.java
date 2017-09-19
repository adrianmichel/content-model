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

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

class ModelWriter {
	private static BufferedWriter out = null;
	private static OutputStream os = null;

	static {
		os = System.out;
		out = new BufferedWriter(new OutputStreamWriter(os));
	}

	static void print(String str) {
		// try
		{
			System.out.print(str);
		}
		/*
		 * catch( IOException e ) { }
		 */}

	static void println(String str) {
		// try
		{
			System.out.println(str);
		}
		/*
		 * catch( IOException e ) { }
		 */ }

	static void println() {
		// try
		{
			System.out.println();
		}
		/*
		 * catch( IOException e ) { }
		 */}

	static void print(int n) {
		System.out.print(n);
	}

	static void println(int n) {
		System.out.println(n);
	}

	static OutputStream getOutputStream() {
		return os;
	}

}
