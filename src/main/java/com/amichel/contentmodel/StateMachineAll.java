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

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.lang3.Validate;

class StateMachineAll implements AbstractStateMachine {
	private Map<String, XX> d = null;
	private int index;

	private class XX {
		private String symbol = null;
		private boolean optional;
		private int _index;

		XX(String symbol, boolean optional, int index) {
			Validate.notNull(symbol);

			this.symbol = symbol;
			this.optional = optional;
			this._index = index;
		}

		String getSymbol() {
			return symbol;
		}

		boolean isOptional() {
			return optional;
		}

		int getIndex() {
			return _index;
		}
	}

	static class State implements AbstractState {
		private Vector<YY> _v = null;
		private StateMachineAll sm = null;

		private class YY {
			private boolean _wasEntered;
			private boolean optional;
			private XX xx = null;

			YY(boolean optional, XX xx) {
				this._wasEntered = false;
				this.optional = optional;
				this.xx = xx;
			}

			boolean wasEntered() {
				return _wasEntered;
			}

			boolean isOptional() {
				return optional;
			}

			void setEntered() {
				_wasEntered = true;
			}

			String getSymbol() {
				return xx.getSymbol();
			}

		}

		State(StateMachineAll sm, int size, Collection<XX> e) {
			Validate.notNull(sm, "The StateMachineAll associated with a state object cannot be null");
			Validate.isTrue(size >= 0, "The number of symbols in a state machine cannot be negative");
			Validate.notNull(e != null, "collection of XX elements cannot be null in StateMachineAll.State");

			this._v = new Vector<YY>(size);
			this._v.setSize(size);
			// init the vector
			e.stream().forEach(xx -> {
				_v.setElementAt(new YY(xx.isOptional(), xx), xx.getIndex());
			});

			this.sm = sm;
		}

		public Iterable<String> getValidTransitions() {
			return new ValidTransitionsIterator(_v);
		}

		private static class ValidTransitionsIterator implements Iterable<String> {
			int n;
			Vector<YY> v = null;

			ValidTransitionsIterator(Vector<YY> v) {
				Validate.notNull(v, "vector cannot be null in ValidTransitionsIterator constructor");

				this.n = 0;
				this.v = v;
				nextIndex();
			}

			private void nextIndex() {
				for (; n < v.size(); n++) {
					YY yy = v.elementAt(n);
					if (!yy.wasEntered())
						break;
				}
			}

			public boolean hasMoreElements() {
				return n < v.size();
			}

			public String nextElement() {
				String o = null;
				if (hasMoreElements()) {
					YY yy = v.elementAt(n++);
					o = yy.getSymbol();
				}
				nextIndex();
				return o;
			}

			@Override
			public Iterator<String> iterator() {
				Iterator<String> it = new Iterator<String>() {

					@Override
					public boolean hasNext() {
						return hasMoreElements();
					}

					@Override
					public String next() {
						return nextElement();
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
				return it;
			}

		}

		public boolean doTransition(String symbol) {
			Validate.notNull(symbol, "The symbol cannot be null in StateMachineAll.State.doTransition()");

			int n = sm.getIndex(symbol);

			Validate.isTrue(n < _v.size());

			if (n < 0)
				// the symbol is not in the language described by the state machine
				return false;
			else {
				YY yy = _v.elementAt(n);
				if (yy.wasEntered()) {
					// cannot add the same symbol twice - only supports optional
					return false;
				} else {
					// mark the symbol as true (existent)
					yy.setEntered();
					return true;
				}
			}
		}

		public boolean isValidTransition(String symbol) {
			Validate.notNull(symbol, "The symbol cannot be null in StateMachineAll.State.doTransition()");

			int n = sm.getIndex(symbol);

			Validate.isTrue(n < _v.size());

			if (n < 0)
				// the symbol is not in the language described by the state machine
				return false;
			else {
				return !(_v.elementAt(n)).wasEntered();
			}
		}

		public boolean canTerminate() {
			for (int n = 0; n < _v.size(); n++) {
				YY yy = _v.elementAt(n);

				if (!yy.wasEntered() && !yy.isOptional())
					return false;
			}

			return true;
		}
	}

	StateMachineAll() {
		this.d = new HashMap<String, XX>();
		this.index = 0;
	}

	boolean addTransition(String symbol, boolean optional) {
		Validate.notNull(symbol, "Cannot add a null symbol to the StateMachineAll");

		// return false if the symbol already there
		return d.put(symbol, new XX(symbol, optional, index++)) == null;
	}

	// returns a new state object set to the start state
	public AbstractState getInitialState() {
		return new State(this, d.size(), d.values());
	}

	public void dump() {
		/** @todo implement the state machine all dump */
	}

	public void setFinalStates(Object o) {
	}

	int getIndex(String symbol) {
		XX xx = d.get(symbol);
		if (xx == null)
			// symobl not in language
			return -1;
		else
			return xx.getIndex();
	}
}
