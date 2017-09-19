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
import java.util.Map;
import java.util.Vector;

import org.apache.commons.lang3.Validate;

import com.amichel.collections.IntegerVector;

/**
 * StateMachine - holds the state transition table and provides state machine
 * functionality the state machine start state is always 0. Invalid transitions
 */
class StateMachineRanges implements AbstractStateMachine {
	// constants
	private static final int START = 0;
	// the state transition table
	private StateTable stateTable;
	// indicates the final states
	private FinalStates finalStates = new FinalStates();

	// the name of the state machine (the name of the model too)
	private final String name;

	// this holds the limits for all the ranges in the state machine.
	// used by the state class to create a vector of ranges
	private LimitsVector lv;

	/**
	 * a map of integers to IntegerVectors each integer vector has indexes to ranges
	 * that have to be checked when terminating through the particular state machine
	 * cannot use a vector because the keys are not necessarily contiguous
	 */
	@SuppressWarnings("serial")
	static class FinalStates extends HashMap<Integer, IntegerVector> {
		// by default, a newly constructed state machine ends on state 0
		// and doesn't have any ranges
		void init() {
			super.put(0, new IntegerVector());
		}

		public void dump() {
			try {
				ModelWriter.println("Final states:");
				for (java.util.Map.Entry<Integer, IntegerVector> e : super.entrySet()) {
					Integer i = e.getKey();
					IntegerVector v = e.getValue();

					ModelWriter.print("\t" + i.toString() + " ");
					v.dump(ModelWriter.getOutputStream());
					ModelWriter.println();
				}
			} catch (IOException e) {
				System.err.println("IOException in StateMachineRanges.dump: " + e.getMessage());
			}
		}

	}

	/**
	 * State - holds the current state. Used by the parser in the validation process
	 * to traverse the DFA graph
	 */
	public static class State implements AbstractState {
		// initally, the state is start
		private int crtState = StateMachineRanges.START;
		// the state machine to which it refers
		private final StateMachineRanges sm;

		// holds the ranges current info for each state: counts and limits
		private RangeVector rv;

		int getCrtState() {
			return crtState;
		}

		void setState(int _state) {
			crtState = _state;
		}

		// constructor - attaches it to a state machine
		State(StateMachineRanges _sm, LimitsVector lv) {
			sm = _sm;
			rv = new RangeVector(lv);
		}

		// calculates the next state, based on the input symbol and the current state
		public boolean doTransition(String symbol) {
			Validate.notNull(symbol);

			return sm.transition(symbol, this);
		}

		public boolean isValidTransition(String symbol) {
			int oldCrtState = crtState;
			RangeVector oldRv = (RangeVector) rv.clone();
			boolean b = doTransition(symbol);
			crtState = oldCrtState;
			rv = oldRv;
			return b;
		}

		// determines if the current state is also a final state
		// which would allow the user to terminate the validation
		public boolean canTerminate() {
			return sm.canTerminate(this);
		}

		// initializes the ranges whose indexes are in "v"
		// used when a transition goes into a start range node
		boolean initStart(IntegerVector v) {
			Validate.notNull(v);

			rv.initStart(v);
			return true;
		}

		// checks that counts are within allowed limits for ranges
		// whose indexes are in "v" and then resets the counters
		// used when a transition leaves a end range state (or node)
		boolean checkAndReset(IntegerVector v) {
			Validate.notNull(v);

			return rv.checkAndReset(v);
		}

		// check that counts are within allowed limits for ranges whose
		// indexes are in "v".
		boolean check(IntegerVector v) {
			Validate.notNull(v);

			return rv.check(v);
		}

		boolean increment(int n) {
			return rv.increment(n);
		}

		public Iterable<String> getValidTransitions() {
			return sm.getValidSymbolsFromState(this);
		}

		public void dump() {
			ModelWriter.print("State: ");
			ModelWriter.print(crtState);
		}
	}

	/**
	 * StateTable - the transitions table - a bidemensional matrix of symbols and
	 * states maps (symbol, current state) pairs to next states note that the states
	 * in the table are not objects of class State but pure integers, for
	 * performance reasons
	 *
	 *
	 * TODO: call this TransitionTable
	 */
	@SuppressWarnings("serial")
	private static class StateTable extends HashMap<String, StateTable.Row> {
		// the number of states in the state machine
		private final int stateNumber;

		/**************************************************
		 * State - a row in the transition table it is an array of integers
		 *************************************************/
		private static class Row {
			// the actual row
			private final Transition[] row;

			// constructor
			// takes the row size as argument
			Row(int n) {
				// create the row
				row = new Transition[n];
				// initialize it with no transitions
				for (int m = 0; m < n; m++) {
					row[m] = InvalidTransition.getInvalidTransition();
				}
			}

			// adds a transition to the row
			boolean addTransition(int from, Transition transition) {
				Validate.notNull(transition);
				Validate.isTrue(from >= 0);

				// if there is already a valid transition there, return false
				// this means the content model is not deterministic
				if (!(row[from]).canAddTransition())
					return false;
				else {
					// set the new state value
					row[from] = transition;
					return true;
				}
			}

			// returns the next transition
			boolean transition(State state) {
				Validate.notNull(state);

				return (row[state.getCrtState()]).toNextState(state);
			}

			boolean isValidTransition(State state) {
				Validate.notNull(state);

				return ((Transition) row[state.getCrtState()]).isValid();
			}

			public void dump() {
				for (int n = 0; n < row.length; n++)
					row[n].dump(n);
				// ModelWriter.println();
			}

		}

		// constructor
		// sets the number of states in the state machine
		StateTable(int _stateNumber) {
			stateNumber = _stateNumber;
		}

		// adds a transition to the table
		// returns false if non-deterministic
		boolean addTransition(String symbol, int from, Transition transition) {
			Validate.notNull(symbol);
			Validate.isTrue(from >= 0);
			Validate.notNull(transition);

			Row row = (Row) get(symbol);
			// if there is no row corresponding to "symbol"
			// create a new row
			if (row == null) {
				row = new Row(stateNumber);
				put(symbol, row);
			}

			return row.addTransition(from, transition);
		}

		// calculates the next state, from the symbol and current state
		boolean transition(String symbol, State state) {
			Validate.notNull(symbol);
			Validate.notNull(state);

			Row row = (Row) get(symbol);
			if (row != null)
				return row.transition(state);
			else
				return false;
		}

		Iterable<String> getValidSymbolsFromState(State state) {
			Vector<String> v = new Vector<String>();

			for (Map.Entry<String, Row> entry : super.entrySet()) {
				String symbol = entry.getKey();
				Row row = entry.getValue();
				if (row.isValidTransition(state))
					v.addElement(symbol);
			}
			return v;
		}

		public void dump() {
			ModelWriter.println("Transitions:");
			for (Map.Entry<String, Row> entry : super.entrySet()) {
				String symbol = entry.getKey();
				Row row = entry.getValue();

				ModelWriter.println("\"" + symbol + "\" ");

				row.dump();
			}

			ModelWriter.println();
		}
	}

	// makes a new state object, to be used on this state machine
	public AbstractState getInitialState() {
		// ModelWriter.println( "************* Getting the initial state
		// ***************" );
		return new State(this, lv);
	}

	// constructor - creates a table with the specified number of states
	StateMachineRanges(String name, int states, LimitsVector lv) {
		Validate.notNull(name);
		Validate.notNull(lv);

		this.name = name;
		finalStates.init();
		stateTable = new StateTable(states);
		this.lv = lv;

	}

	// adds a transition to the table
	private boolean addTransition(String symbol, int from, Transition transition) {
		Validate.notNull(symbol);
		Validate.isTrue(from >= 0);
		Validate.notNull(transition);

		return stateTable.addTransition(symbol, from, transition);
	}

	// sets the final states
	private void setFinalStates(FinalStates finalStates) {
		Validate.notNull(finalStates);
		this.finalStates = finalStates;
	}

	public void setFinalStates(Object o) {
		setFinalStates((FinalStates) o);
	}

	// calculates the next state
	boolean transition(String symbol, State state) {
		Validate.notNull(symbol);
		Validate.notNull(state);

		return stateTable.transition(symbol, state);
	}

	boolean canTerminate(State state) {
		if (isFinal(state.getCrtState()))
			// checks that all the ranges terminating in this state
			// have the count within limits
			return state.check(finalStates.get(state.getCrtState()));
		else
			return false;
	}

	// tests if a state is final
	boolean isFinal(int state) {

		// ModelWriter.print( "isFinal - " + name + ": " );
		boolean b = finalStates.containsKey(new Integer(state));
		// ModelWriter.println( b );
		return b;

	}

	public void dump() {
		ModelWriter.println("------------------- State Machine -----------------");
		ModelWriter.println("Name: " + name);
		ModelWriter.print("Ranges: ");
		lv.dump();
		ModelWriter.println();
		stateTable.dump();

		ModelWriter.println("Start: 0");
		finalStates.dump();
		ModelWriter.println();
	}

	private static interface Transition {
		public boolean toNextState(State state);

		public boolean canAddTransition();

		public void dump(int from);

		public boolean isValid();
	}

	private static class ValidTransition implements Transition {
		protected int nextState;

		ValidTransition(int _nextState) {
			nextState = _nextState;
		}

		public boolean toNextState(State state) {
			Validate.notNull(state);

			state.setState(nextState);
			return true;
		}

		// this indicates that we cannot replace this transition
		// in the transition table or it is a nondeterministic FA
		public boolean canAddTransition() {
			return false;
		}

		public void dump(int from) {
			ModelWriter.print("\t");
			ModelWriter.print(from);
			ModelWriter.print(" -> ");
			ModelWriter.print(nextState);
			ModelWriter.println();
		}

		public boolean isValid() {
			return true;
		}
	}

	private static class InvalidTransition implements Transition {
		private static InvalidTransition it = new InvalidTransition();

		static InvalidTransition getInvalidTransition() {
			return it;
		}

		public boolean toNextState(State state) {
			return false;
		}

		// initially, the state machine has only invalid transitions
		// and they can be replaced in the state machine by other valid transitions
		public boolean canAddTransition() {
			return true;
		}

		public void dump(int from) { /* ModelWriter.print( "Invalid transition" ); */
		}

		public boolean isValid() {
			return false;
		}
	}

	/**
	 * goes from a state to a range start This transition will push the ranges that
	 * start onto the stack The order in which they are pushed is important, and
	 * should reflect the range nesting in the syntax tree
	 */
	private static class TransitionToStartRange extends ValidTransition {
		private IntegerVector v;

		TransitionToStartRange(int nextState, IntegerVector v) {
			super(nextState);
			this.v = v;
		}

		public boolean toNextState(State state) {
			Validate.notNull(state);

			if (super.toNextState(state))
				return state.initStart(v);
			else
				return false;
		}

		public void dump(int from) {
			try {
				ModelWriter.print("\t");
				ModelWriter.print(from);
				ModelWriter.print(" -> ");
				ModelWriter.print(nextState);
				ModelWriter.print(" (-SR), ");
				ModelWriter.print("Init: ");
				v.dump(ModelWriter.getOutputStream());
				ModelWriter.println();
			} catch (IOException e) {
				System.err.println(
						"IOException in StateMachineRanges.TransitionToStartRange.dump( int ): " + e.getMessage());
			}
		}
	}

	/**
	 * goes from a range end state to a non start range state (can be end range
	 * state or any other state) This transition will pop the ending ranges from the
	 * stack, checking that the count is within ranges in the process
	 */
	private class TransitionFromEndRange extends ValidTransition {
		private IntegerVector v;

		TransitionFromEndRange(int nextState, IntegerVector v) {
			super(nextState);
			Validate.notNull(v);

			this.v = v;
		}

		public boolean toNextState(State state) {
			if (super.toNextState(state))
				return state.check(v);
			else
				return false;
		}

		public void dump(int from) {
			try {
				ModelWriter.print("\t");
				ModelWriter.print(from);
				ModelWriter.print(" -> ");
				ModelWriter.print(nextState);
				ModelWriter.print(" (ER-), ");
				ModelWriter.print("Check: ");
				v.dump(ModelWriter.getOutputStream());
				ModelWriter.println();
			} catch (IOException e) {
				System.err.println("IOException in StateMachineRanges.dump( int ): " + e.getMessage());
			}
		}

		protected IntegerVector getCheck() {
			return v;
		}
	}

	/**
	 * goes from a range end to a range start This transition will pop any other
	 * range that ends into the same end node, checking that their count is within
	 * the range, then increment the TOS, then push onto the stack other ranges that
	 * start in the same start node The order in which they are pushed is important,
	 * and should reflect the range nesting in the syntax tree
	 */
	private class TransitionFromEndToStartRangeIncrement extends TransitionFromEndToStartRangeNoIncrement {
		int inc;

		TransitionFromEndToStartRangeIncrement(int nextState, IntegerVector _v1, IntegerVector _v2, int _inc) {
			super(nextState, _v1, _v2);
			inc = _inc;
		}

		public boolean toNextState(State state) {
			if (state.increment(inc))
				return super.toNextState(state);
			else
				return false;
		}

		public void dump(int from) {
			try {
				ModelWriter.print("\t");
				ModelWriter.print(from);
				ModelWriter.print(" -> ");
				ModelWriter.print(nextState);
				ModelWriter.print(" (ER++-SR), ");
				ModelWriter.print("inc = ");
				ModelWriter.print(inc);
				ModelWriter.print(", ");
				ModelWriter.print("Check: ");
				getCheck().dump(ModelWriter.getOutputStream());
				ModelWriter.print(", Init: ");
				getInit().dump(ModelWriter.getOutputStream());
				ModelWriter.println();
			} catch (IOException e) {
				System.err.println(
						"IOException in StateMachineRanges.TransitionFromEndToStartRangeIncrement.dump( int ): "
								+ e.getMessage());
			}
		}
	}

	/**
	 * goes from a range end to a range start, but which doesn't correspond to the
	 * same range, so it will not do the increment This transition will pop any
	 * other range that ends into the same end node, checking that their count is
	 * within the range, then push onto the stack other ranges that start in the
	 * same start node The order in which they are pushed is important, and should
	 * reflect the range nesting in the syntax tree
	 */
	private class TransitionFromEndToStartRangeNoIncrement extends TransitionFromEndRange {
		private IntegerVector v;

		TransitionFromEndToStartRangeNoIncrement(int nextState, IntegerVector v1, IntegerVector v2) {
			super(nextState, v2);
			this.v = v1;
		}

		public boolean toNextState(State state) {
			if (super.toNextState(state))
				return state.initStart(v);
			else
				return true;
		}

		public void dump(int from) {
			try {
				ModelWriter.print("\t");
				ModelWriter.print(from);
				ModelWriter.print(" -> ");
				ModelWriter.print(nextState);
				ModelWriter.print(" (ER-SR), ");
				ModelWriter.print("Check: ");
				getCheck().dump(ModelWriter.getOutputStream());
				ModelWriter.print(", Init: ");
				v.dump(ModelWriter.getOutputStream());
				ModelWriter.println();
			} catch (IOException e) {
				System.err.println(
						"IOException in StateMachineRanges.TransitionFromEndToStartRangeNoIncrement.dump( int ): "
								+ e.getMessage());
			}
		}

		protected IntegerVector getInit() {
			return v;
		}
	}

	@SuppressWarnings("serial")
	private static class RangeVector extends Vector<Range> {
		RangeVector(LimitsVector lv) {
			for (Limits limits : lv)
				addElement(new Range(limits));
		}

		private RangeVector(RangeVector rv) {
			for (Range range : rv) {
				addElement(range.clone());
			}
		}

		public RangeVector clone() {
			return new RangeVector(this);
		}

		boolean increment(int n) {
			Range range = (Range) elementAt(n);
			return range.increment();
		}

		Range getRange(int n) {
			return elementAt(n);
		}

		Range getRange(Integer n) {
			return elementAt(n.intValue());
		}

		// sets the counters to 1, as to the entry into the range
		boolean initStart(IntegerVector v) {
			for (int n = 0; n < v.size(); n++) {
				Range r = getRange(v.elementAt(n));
				r.init();
			}
			return true;
		}

		boolean checkAll() {
			for (int n = 0; n < size(); n++) {
				if (!getRange(n).check())
					return false;
			}
			return true;
		}

		// non-destructive check
		boolean check(IntegerVector v) {
			for (Integer n : v) {
				Range r = getRange(n);
				if (!r.check())
					return false;
			}
			return true;
		}

		void reset(IntegerVector v) {
			for (int n = 0; n < v.size(); n++) {
				Range r = getRange(v.elementAt(n));
				r.reset();
			}
		}

		boolean checkAndReset(IntegerVector v) {
			for (int n = 0; n < v.size(); n++) {
				Range r = getRange(v.elementAt(n));
				if (!r.checkAndReset())
					return false;
			}
			return true;
		}

		public void dump() {
			for (int n = 0; n < size(); n++) {
				ModelWriter.print(n);
				ModelWriter.print(" - ");
				elementAt(n).dump();
				ModelWriter.println();
			}
		}
	}

	private static class Range {
		Limits limits;
		private int count;

		private Range(Range range) {
			limits = (Limits) range.getLimits().clone();
			count = range.getCount();
		}

		Range(Limits _limits) {
			// TODO: check _min, _max for consistency
			limits = _limits;
			count = 0;
		}

		private Limits getLimits() {
			return limits;
		}

		private int getCount() {
			return count;
		}

		boolean increment() {
			boolean b = limits.checkMax(++count);
			return b;
		}

		boolean check() {
			return limits.checkMin(count) && limits.checkMax(count);
		}

		void init() {
			count = 1;
		}

		void reset() {
			count = 0;
		}

		boolean checkAndReset() {
			boolean b = check();
			reset();
			return b;
		}

		public void dump() {
			limits.dump();
			ModelWriter.print("count: ");
			ModelWriter.print(count);
		}

		public Range clone() {
			return new Range(this);
		}
	}

	boolean addValidTransition(String symbol, int from, int to) {
		return addTransition(symbol, from, new ValidTransition(to));
	}

	boolean addTransitionToStartRange(String symbol, int from, int to, IntegerVector v) {
		return addTransition(symbol, from, new TransitionToStartRange(to, v));
	}

	boolean addTransitionFromEndRange(String symbol, int from, int to, IntegerVector v) {
		return addTransition(symbol, from, new TransitionFromEndRange(to, v));
	}

	boolean addTransitionFromEndToStartRangeIncrement(String symbol, int from, int to, IntegerVector v1,
			IntegerVector v2, int pos) {
		return addTransition(symbol, from, new TransitionFromEndToStartRangeIncrement(to, v1, v2, pos));
	}

	boolean addTransitionFromEndToStartRangeNoIncrement(String symbol, int from, int to, IntegerVector v1,
			IntegerVector v2) {
		return addTransition(symbol, from, new TransitionFromEndToStartRangeNoIncrement(to, v1, v2));
	}

	Iterable<String> getValidSymbolsFromState(State state) {
		return stateTable.getValidSymbolsFromState(state);
	}
}
