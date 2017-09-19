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

import java.io.IOException;
import java.util.HashMap;
import java.util.Vector;

import org.apache.commons.lang3.Validate;

import com.amichel.collections.IntegerSet;

/**************************************************
 * StateMachine - holds the state transition table and provides state machine
 * functionality the state machine start state is always 0. Invalid transitions
 * are marked with NOTRANSITION (-1)
 ***************************************************/
class StateMachineNoRanges implements AbstractStateMachine {
	// constants
	private static final int START = 0;
	private static final int NOTRANSITION = -1;
	// the state transition table
	private StateTable stateTable;
	// indicates the final states
	private IntegerSet finalStates;

	// the name of the state machine (the name of the model too)
	private final String name;

	/**************************************************
	 * State - holds the current state. Used by the parser in the validation process
	 * to traverse the DFA graph
	 *************************************************/
	public static class State implements AbstractState {
		// initally, the state is start
		private int crtState = StateMachineNoRanges.START;
		// the state machine to which it refers
		private StateMachineNoRanges sm;

		// constructor - attaches it to a state machine
		State(StateMachineNoRanges _sm) {
			sm = _sm;
		}

		// calculates the next state, based on the input symbol and the current state
		public boolean doTransition(String symbol) {
			Validate.notNull(symbol);

			int n = sm.transition(symbol, crtState);
			if (n == StateMachineNoRanges.NOTRANSITION)
				return false;
			else {
				crtState = n;
				return true;
			}
		}

		public boolean isValidTransition(String symbol) {
			Validate.notNull(symbol);

			return sm.transition(symbol, crtState) != StateMachineNoRanges.NOTRANSITION;
		}

		// determines if the current state is also a final state
		// which would allow the user to terminate the validation
		public boolean canTerminate() {
			return sm.isFinal(crtState);
		}

		public Iterable<String> getValidTransitions() {
			return sm.getValidSymbolsFromState(crtState);
		}
	}

	/**************************************************
	 * StateTable - the transitions table - a bidemensional matrix of symbols and
	 * states maps (symbol, current state) pairs to next states note that the states
	 * in the table are not objects of class State but pure integers, for
	 * performance reasons
	 *************************************************/
	@SuppressWarnings("serial")
	private static class StateTable extends HashMap<String, StateTable.Row> {
		// the number of states in the state machine
		private final int stateNumber;

		/**************************************************
		 * State - a row in the transition table it is an array of integers
		 *************************************************/
		private static class Row {
			private static final int NOTRANSITION = -1;
			// the actual row
			private final int[] row;

			// constructor
			// takes the row size as argument
			Row(int n) {
				// create the row
				row = new int[n];
				// initialize it with no transitions
				for (int m = 0; m < n; m++) {
					row[m] = NOTRANSITION;
				}
			}

			// adds a transition to the row
			boolean addTransition(int from, int to) {
				Validate.isTrue(from >= 0 && to > 0);

				// if there is already a transition there, return false
				// this means the content model is not deterministic
				if (row[from] != NOTRANSITION)
					return false;
				// set the new state value
				row[from] = to;
				return true;
			}

			// returns the next transition
			int transition(int from) {
				return row[from];
			}

			public void dump() {
				for (int n = 0; n < row.length; n++) {
					if (row[n] != -1) {
						ModelWriter.print(n);
						ModelWriter.print(" -> ");
						ModelWriter.print(row[n]);
						ModelWriter.print(", ");
					}
				}
				ModelWriter.println();
			}
		}

		// constructor
		// sets the number of states in the state machine
		StateTable(int _stateNumber) {
			stateNumber = _stateNumber;
		}

		// adds a transition to the table
		// returns false if non-deterministic
		boolean addTransition(String symbol, int from, int to) {
			Validate.notNull(symbol);
			Validate.isTrue(from >= 0 && to > 0);

			Row row = (Row) get(symbol);
			// if there is no row corresponding to "symbol"
			// create a new row
			if (row == null) {
				row = new Row(stateNumber);
				put(symbol, row);
			}

			return row.addTransition(from, to);
		}

		// calculates the next state, from the sybmol and current state
		int transition(String symbol, int from) {
			Validate.notNull(symbol);
			Validate.isTrue(from >= 0);

			Row row = (Row) get(symbol);

			if (row == null)
				// throw something
				return -1;
			else
				return row.transition(from);
		}

		Iterable<String> getValidSymbolsFromState(int from) {
			Vector<String> v = new Vector<String>();
			for (String symbol : super.keySet()) {
				Row row = get(symbol);

				if (row.transition(from) != NOTRANSITION)
					v.addElement(symbol);
			}
			return v;
		}

		public void dump() {
			for (String symbol : super.keySet()) {
				ModelWriter.print("\"" + symbol + "\" ");

				Row row = get(symbol);
				row.dump();
			}

			ModelWriter.println();
		}
	}

	// makes a new state object, to be used on this state machine
	public AbstractState getInitialState() {
		// ModelWriter.println( "************* Getting the initial state
		// ***************" );
		return new State(this);
	}

	// constructor - creates a table with the specified number of states
	StateMachineNoRanges(String name, int states) {
		Validate.notNull(name);
		Validate.isTrue(states > 0);

		this.name = name;
		stateTable = new StateTable(states);
		// by default, a newly constructed state machine starts and ends on state 0;
		finalStates = new IntegerSet();
		finalStates.add(0);
	}

	// adds a transition to the table
	boolean addTransition(String symbol, int from, int to) {
		return stateTable.addTransition(symbol, from, to);
	}

	// sets the final states
	private void setFinalStates(IntegerSet finalStates) {
		this.finalStates = finalStates;
	}

	public void setFinalStates(Object o) {
		setFinalStates((IntegerSet) o);
	}

	// calculates the next state
	int transition(String symbol, int from) {
		/*
		 * ModelWriter.print( "Trans - " + name + ": \"" + symbol + "\" ");
		 * ModelWriter.print( from ); ModelWriter.print( "->" );
		 */
		int n = stateTable.transition(symbol, from);
		// ModelWriter.println( n );
		return n;
	}

	// tests if a state is final
	boolean isFinal(int state) {
		// ModelWriter.print( "isFinal - " + name + ": " );
		boolean b = finalStates.contains(new Integer(state));
		// ModelWriter.println( b );
		return b;

	}

	Iterable<String> getValidSymbolsFromState(int from) {
		return stateTable.getValidSymbolsFromState(from);
	}

	public void dump() {
		try {
			ModelWriter.println("--------------- State Machine ---------------");
			ModelWriter.println("Name: " + name);
			stateTable.dump();
			ModelWriter.println("Start: 0");
			ModelWriter.print("Finals: ");
			finalStates.dump(ModelWriter.getOutputStream());
			ModelWriter.println();
			ModelWriter.println();
		} catch (IOException e) {
			System.err.println("IOException in StateMachineNoRanges.dump: " + e.getMessage());
		}
	}
}
