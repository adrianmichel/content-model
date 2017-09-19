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

public abstract class AbstractExpression extends SyntaxTree

{
	AbstractExpression(String name) {
		super(name);
	}

	public abstract void push();

	public abstract void pop();

	public void pop(int n) {
		while (n-- > 0)
			pop();
	}

	public void plus() {
		plus("");
	}

	public void star() {
		star("");
	}

	public void opt() {
		opt("");
	}

	public void neutral() {
		neutral("");
	}

	public void range(int min, int max) throws BadLimitsException {
		range("", min, max);
	}

	public void or() {
		or("");
	}

	public void and() {
		and("");
	}

	public void all() {
		all("");
	}

	public abstract void plus(String str);

	public abstract void star(String str);

	public abstract void opt(String str);

	public void range(String str, int min, int max) throws BadLimitsException {
		precalcRanges(str, min, max);
	}

	public abstract void or(String str);

	public abstract void and(String str);

	public abstract void all(String str);

	public abstract void neutral(String str);

	public abstract void symbol(String str);

	public void parse(String str) throws BadLimitsException {
		String s;
		for (int n = 0; n < str.length(); n++) {
			char c = str.charAt(n);

			switch (c) {
			case '(':
				push();
				break;
			case ')':
				pop();
				break;
			case '^':
				pop();
				break;
			case '*':
				star();
				break;
			case '+':
				plus();
				break;
			case '?':
				opt();
				break;
			case ',':
				and();
				break;
			case '|':
				or();
				break;
			case '&':
				all();
				break;
			case ' ':
			case '\t':
			case '\n':
			case '\r':
				// ignore white space
				break;
			case '[':
				n++;
				n = makeRange(str, n);
				break;
			default:
				n = makeSymbol(str, n);
				break;
			}
		}
	}

	private int makeSymbol(String str, int n) {
		String s = new String();
		boolean f = true;
		for (; n < str.length() && f; n++) {
			char ch = str.charAt(n);
			switch (ch) {
			case '(':
			case ')':
			case '*':
			case '+':
			case '?':
			case '[':
			case ',':
			case '|':
			case '&':
			case ' ':
			case '^':
			case '\t':
			case '\n':
			case '\r':
				symbol(s);
				return --n;
			default:
				s += str.substring(n, n + 1);
				break;
			}
		}
		symbol(s);
		return n;
	}

	/**
	 * makeRange calculates the numeric range based on the range expression:
	 * [min,max], where max can be '*', indicating no max limit. The format of the
	 * expression needs to be exactly this, or the method will fail. The method is
	 * passed a string and the location in the string where "min" starts.
	 *
	 * @param str
	 *            The string containing the range expression
	 * @param n
	 *            The location in the string where "min" starts.
	 * @return
	 * @exception BadLimitsException
	 */
	private int makeRange(String str, int n) throws BadLimitsException {
		String s;
		int min;
		int max;

		// position in the string of the separator comma
		int m = str.indexOf(',', n);
		// s is the string containing min
		s = str.substring(n, m);
		// get the numeric value of min
		min = Integer.parseInt(s);

		// move the start past the comma separator
		n = ++m;
		// m points to the closing ']'
		m = str.indexOf(']', n);
		// s is the string containing max
		s = str.substring(n, m);
		// if the max value is '*', then max is -1, indicating no max limit
		if (s.equals("*"))
			max = -1;
		else
			// else max is the numeric value of the string
			max = Integer.parseInt(s);
		// in case we are in one of these special cases, reduce the ranges
		// to their simpler equivalent (*, +, ? or none, in case it is 1,1)
		precalcRanges("", min, max);
		// return the position in the string where the content model expression will
		// continue parsing
		return m;
	}

	private void precalcRanges(String str, int min, int max) throws BadLimitsException {
		if (min == 0 && max == 1)
			opt(str);
		else if (min == 0 && max == -1)
			star(str);
		else if (min == 1 && max == -1)
			plus(str);
		else if (min != 1 || max != 1)
			realRange(str, min, max);
	}

	protected abstract void realRange(String str, int min, int max) throws BadLimitsException;
}