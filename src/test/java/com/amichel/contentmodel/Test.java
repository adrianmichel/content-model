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

import org.junit.Assert;

public class Test {
	static class TestCase {
		private String expression;
		private String[] match;
		private String[] noMatch;

		public TestCase(String expression, String[] match, String[] noMatch) {
			this.expression = expression;
			this.match = match;
			this.noMatch = noMatch;
		}

		public String getExpression() {
			return expression;
		}

		public String[] getMatch() {
			return match;
		}

		public String[] getNoMatch() {
			return noMatch;
		}
	}

	private TestCase[] tc1 = { new TestCase("a?,b", new String[] { "a,b", "b" }, new String[] { "a", "a,b,b" }),
			new TestCase("(a,b,e,f)*|(c,d)|(g,h)", new String[] { "c,d", "a,b,e,f,a,b,e,f", "g,h" },
					new String[] { "a,b,e,f,a,b", "a,b,e,f,c,d" }),
			new TestCase("(a[2,3],b[2,3])[5,6]", new String[] { "a,a,b,b,a,a,a,b,b,b,a,a,b,b,b,a,a,a,b,b,a,a,a,b,b,b" },
					new String[] { "a,a,b,b,a,a,a,b,b,b,a,a,b,b,b,a,a,a,b,b" }),
			new TestCase("(a|b)+", new String[] { "a,b,a,a,a,b,b", "a,b" }, new String[] { "", "c" }),
			new TestCase("a?", new String[] { "a", "" }, new String[] { "a,a", "b" }),
			new TestCase("(b),(b*,a)*", new String[] { "b,b,b,a,b,b,b,a,b,b,b,b,b,b,a", "b", "b,a" },
					new String[] { "b,b", "b,a,b" }),
			new TestCase("a?,b?,c?", new String[] { "", "a", "b", "c", "a,b", "a,c", "b,c", "a,b,c" },
					new String[] { "c,a", "a,a" }),
			new TestCase("(a+,b+,c+)?,d", new String[] { "a,b,c,d", "a,a,b,b,c,c,d", "d" }, new String[] { "b,c", "" }),
			new TestCase("a?,b?,c?", new String[] { "a,b,c", "a,b", "a,c" }, new String[] { "b,a", "a,a" }),
			new TestCase("((a?,b?,h,i)|(c*,d?,k)+|e|(f,g))",
					new String[] { "a,b,h,i", "h,i", "k", "e", "f,g", "c,c,c,c,d,k", "c,d,k,k,d,k,c,c,c,d,k" },
					new String[] { "a,a", "e,f", "g,f", "c", "c,d,k,k,d,c,c,c,d,k" }),
			new TestCase("(a*,b?,c)", new String[] { "a,c", "a,a,c", "b,c", "c" },
					new String[] { "a", "a,b,b", "a,c,b" }),
			new TestCase("a,b,c*", new String[] { "a,b", "a,b,c", "a,b,c,c,c" }, new String[] { "c", "a,c" }),
			new TestCase("(a|b|c)[2,4]", new String[] { "a,a", "a,b", "c,c,b,a" },
					new String[] { "a", "a,b,c,a,a", "" }),
			new TestCase("(a|b)[2,3]", new String[] { "a,a,a", "b,b", "a,b,a", "b,a,b" },
					new String[] { "a", "a,a,a,a" }),
			new TestCase("((a|b)+),c,b,b", new String[] { "a,a,a,c,b,b", "b,a,a,b,a,a,a,b,c,b,b" },
					new String[] { "c,b,b", "" }),
			new TestCase("a[5,6]", new String[] { "a,a,a,a,a", "a,a,a,a,a,a" },
					new String[] { "a,a,a,a", "a,a,a,a,a,a,a" }),
			new TestCase("(a[5,6],b)[1,2]", new String[] { "a,a,a,a,a,a,b", "a,a,a,a,a,b,a,a,a,a,a,a,b" },
					new String[] { "a,a,a,a", "a,a,a,a,a,a,a", "a,a,a,a,a,a,a,a,a,a,a,a,a", "a,a,a,a,a,a,a,a,a,a" }),
			new TestCase("(a|b)[5,6]",
					new String[] { "a,a,a,a,a,a", ",a,a,a,a,a,a", "a,b,a,b,a", "b,b,b,b,b,b", "a,a,a,a,a,a" },
					new String[] { "a,a,a,a", "a,a,a,a,a,a,a", "c", "b,b,b,b,b,b,b,b" }),
			new TestCase("tree|flower", new String[] { "tree", "flower" }, new String[] { "plant" }) };

	private static final String AMBIGUOUS_CME[] = {

			"(a+,b+,c+)?,c", "(a|b)*,a,b,b", "a*|(a,b)", "(a,b)|(a,c)", "(a,b)*|(a,c)", "a,(b,a)*,(b|<empty>)",
			"a|(a,b)", "a[2,4],(a|b)[1,1],(a,b,c?)*", "a[5,6]|a" };

	private static class X {
		private SyntaxTree s;

		X(String expression) throws BadLimitsException, AmbiguousContentModelException {
			AbstractExpression e = new DirectNotationFE("");
			this.s = (SyntaxTree) e;

			e.parse(expression);
			e.dumpExpression();
			e.compile();

			e.compile();
			e.dump();
			e.dumpStateMachine();
		}

		boolean run(String value) {
			AbstractState as = s.getInitialState();
			return runDFA(as, s, value);
		}
	}

	@org.junit.Test
	public void testNonAmbiguous() throws BadLimitsException, AmbiguousContentModelException {
		for (TestCase tc : tc1) {
			X x = new X(tc.getExpression());
			for (String match : tc.getMatch()) {
				System.out.println(
						"testing match - expression: \"" + tc.getExpression() + "\", value: \"" + match + "\"");
				boolean b = x.run(match);
				System.out.println("\tresult: " + (b ? "match" : "no match"));

				Assert.assertTrue(b);
			}

			for (String noMatch : tc.getNoMatch()) {
				System.out.println(
						"testing no match - expression: \"" + tc.getExpression() + "\", value: \"" + noMatch + "\"");
				Assert.assertFalse(x.run(noMatch));
			}
		}
	}

	@org.junit.Test
	public void testAmbiguous() throws BadLimitsException, AmbiguousContentModelException {
		for (String tc : AMBIGUOUS_CME) {
			try {
				System.out.println("testing expression: \"" + tc + "\"");
				X x = new X(tc);
				System.out.println("\"" + tc + "\" is deterministic");
				Assert.fail();
			} catch (AmbiguousContentModelException e) {
				System.out.println("\"" + tc + "\" is ambiguous");
			}
		}
	}

	private static boolean runDFA(AbstractState state, SyntaxTree m, String str) {
		ModelWriter.println("--------- Running the State Machine ---------");
		boolean b = true;
		String s = new String();
		boolean inWord = false;

		for (int n = 0; n < str.length() && b; n++) {

			char c = str.charAt(n);
			switch (c) {
			case ',':
			case ' ':
			case '\t':
			case '\n':
			case '\r':
				if (inWord) {
					if (!state.doTransition(s)) {
						b = false;
						break;
					}
					inWord = false;
					s = "";
				}
				break;
			default:
				inWord = true;
				s += str.substring(n, n + 1);
				break;
			}

		}
		if (inWord) {
			if (!state.doTransition(s))
				b = false;
		}

		if (b)
			b = state.canTerminate();

		ModelWriter.print("\"" + str + "\" ");
		ModelWriter.println((b ? "matches " : "doesn't match ") + m.toString());

		return b;
	}

}
