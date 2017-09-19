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
 * Abstract base class for all the state machines This interface needs not be
 * visible outside the package
 */
interface AbstractStateMachine {
	/**
	 * Generates a new state object, set to the initial state
	 *
	 * @return The state object
	 */
	abstract AbstractState getInitialState();

	public abstract void dump();

	/**
	 * Sets the final states in the state machine
	 *
	 * @todo this should be changes so it uses some typed value instead of Object -
	 *       a new interface should be defined and used for final states
	 * @param o
	 */
	abstract void setFinalStates(Object o);
}
