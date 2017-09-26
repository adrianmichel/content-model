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

import java.util.Stack;
import java.util.Vector;

import org.apache.commons.lang3.Validate;

/**
 * these methods populate the syntax tree using the reverse Polish notation so
 * (a,b)*|(c,d) will be expressed as: pushOr pushStar pushAnd addOperand( "a" )
 * addOperand( "b" ) pop(2) (2 because of the And and Star -brings us back to
 * the root (OR) level) pushAnd addOperand( "c" ) addOperand( "d" ) Note:
 * addOperand doesn't change TOS, so there is no need to pop after a addOperand
 */

public class ReversePolishNotationFE extends AbstractExpression {
	// these two are used when constructing the syntax tree with push/pop operations
	private ModelNode crtNode = null;
	private Stack<ModelNode> binaryExpressionStack = new Stack<ModelNode>();

	public ReversePolishNotationFE(String name) {
		super(name);
	}

	@Override
	public void star(String name) {
		pushOperator(makeStarNode(name));
	}

	@Override
	public void plus(String name) {
		pushOperator(makePlusNode(name));
	}

	@Override
	public void opt(String name) {
		pushOperator(makeOptNode(name));
	}

	@Override
	public void neutral(String name) {
		pushOperator(makeNeutralNode(name));
	}

	@Override
	public void or(String name) {
		pushOperator(makeOrNode(name));
	}

	@Override
	public void and(String name) {
		pushOperator(makeAndNode(name));
	}

	@Override
	public void all(String name) {
		pushOperator(makeAllNode(name));
	}

	@Override
	protected void realRange(String name, int min, int max) throws BadLimitsException {
		pushOperator(makeRangeNode(name, min, max));
	}

	private void pushOperator(OperatorNode node) {
		Validate.notNull(node);
		binaryExpressionStack.push(crtNode = addNode(crtNode, node));
	}

	public void symbols(Vector<String> operands) {
		Validate.notNull(operands);

		for (String operand : operands)
			// create the word node for the string n
			symbol(operand);
	}

	@Override
	public void symbol(String operand) {
		addNode(crtNode, makeSymbolNode(operand));
	}

	@Override
	public void pop() {
		binaryExpressionStack.pop();
		if (!binaryExpressionStack.empty())
			crtNode = binaryExpressionStack.peek();
	}

	//
	@Override
	public void push() {
	}
}
