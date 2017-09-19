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

import com.amichel.collections.NaryTree;

public class DirectNotationFE extends AbstractExpression {
	private class XTree extends NaryTree<ModelNode> {
		private NaryTree.Node<ModelNode> crtNode = null;
		private char crtOp = 0;

		// this is normally used for symbols
		void addMakeCrt(NaryTree.Node< ModelNode > node) {
			if (crtNode == null)
				crtNode = setRoot(node);
			else
				crtNode = crtNode.addSiblingToBack(node);
		}

		// this is normally used for and/or operators
		void add(NaryTree.Node< ModelNode > node) {
			if (crtOp == 0) {
				insertParentToBack(crtNode, node);
				crtOp = 1;
			}
		}

		void add1(NaryTree.Node< ModelNode > node) {
			insertParentToBack(crtNode, node);
			crtNode = node;
		}

		void addSubtree(NaryTree< ModelNode > tree) {
			NaryTree.Node< ModelNode > node = tree.getRoot();
			if (isRoot(crtNode))
				addChildTreeToBack(crtNode, tree);
			else
				this.addSiblingTreeToBack(crtNode, tree);
			crtNode = node;
		}
	}

	private Stack< XTree > stack = new Stack< XTree >();
	private XTree crtTree = new XTree();

	public DirectNotationFE(String name) {
		super(name);
	}

	public DirectNotationFE(String name, String expression) throws BadLimitsException, AmbiguousContentModelException {
		super(name);

		parse(expression);
		compile();
	}

	void addUnaryOp(SyntaxTree.ModelNode op) {
		crtTree.add1(op);
	}

	void addNaryOp(SyntaxTree.ModelNode op) {
		crtTree.add(op);
	}

	public void star(String name) {
		addUnaryOp(makeStarNode(name));
	}

	public void plus(String name) {
		addUnaryOp(makePlusNode(name));
	}

	public void opt(String name) {
		addUnaryOp(makeOptNode(name));
	}

	protected void realRange(String name, int min, int max) throws BadLimitsException {
		addUnaryOp(makeRangeNode(name, min, max));
	}

	public void neutral(String name) {
		addUnaryOp(makeNeutralNode(name));
	}

	public void and(String name) {
		addNaryOp(makeAndNode(name));
	}

	public void or(String name) {
		addNaryOp(makeOrNode(name));
	}

	public void all(String name) {
		addNaryOp(makeAllNode(name));
	}

	public void symbol(String w) {
		crtTree.addMakeCrt(makeSymbolNode(w));
	}

	public void pop() {
		XTree tree = (XTree) stack.pop();
		tree.addSubtree(crtTree);
		crtTree = tree;
	}

	public void push() {
		stack.push(crtTree);
		crtTree = new XTree();
	}

	public void parse(String str) throws BadLimitsException {
		super.parse(str);
		Node<ModelNode> root = crtTree.getRoot();
		if (root != null)
			setRoot(crtTree.getRoot());
	}
}
