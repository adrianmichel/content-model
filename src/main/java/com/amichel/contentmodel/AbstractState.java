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

/**
 * Abstract base class for all the state classes
 */
public interface AbstractState {
	/**
	 * gets all valid transitions from the current state
	 *
	 * @return An iterator of the valid transitions
	 */
	public Iterable<String> getValidTransitions();

	/**
	 * this generates a transition from the current state to the next state, on the
	 * input symbol "word"
	 *
	 * @param word
	 * @return
	 */
	public boolean doTransition(String word);

	/**
	 * This checks if the symbol "word" can generate a valid transition, without
	 * doing the actual transition.
	 *
	 * @param word
	 *            The symbol to be checked
	 * @return true if the symbol would generate a valid transition, false otherwise
	 */
	public boolean isValidTransition(String word);

	/**
	 * this indicates if the state machine can terminate this is more than showing
	 * that the current state is a final state, for state machines like the one that
	 * checks ranges
	 *
	 * @return If the current state is a final state, return true, else return false
	 */
	public boolean canTerminate();
}
