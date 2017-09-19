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
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;

import com.amichel.collections.IntegerSet;
import com.amichel.collections.IntegerVector;
import com.amichel.collections.NaryTree;
import com.amichel.contentmodel.SyntaxTree.ModelNode;

/***************************************************
 * the new content model validation class creates the syntax tree and generates
 * the corresponding DFA using one of two algorithms: - one which also
 * determines the content model expression ambiguousness, but doesn't handle
 * ranges (interprets [0, n] as "*" and [ m, n ] as "+" this algorithm is based
 * on the functions nullable, firstpos, lastpos and followpos - another one that
 * handles ranges, but is less efficient than the first one
 *
 * In the syntax tree all non-leaf nodes are operators (AND, OR, *, +, ? or
 * range) and all the leaf nodes are operands (symbols in the alphabet)
 *
 * All content models (operator ALL: & ) are supported with the limitation that
 * only optional first level elements are accepted. For example: a&b&c, a?&b&c?
 * are accepted. a&(b,c) , a?&(b,c)& not accepted
 *
 *
 * TODO: create iterators for the class tree to traverse the tree in
 * post/preorder
 ***************************************************/
public class SyntaxTree extends NaryTree<ModelNode> {
	// the name of the model
	// can be used to identify the model in the cache
	private final String name;

	// maps positions (integers) to the respective nodes
	// used in the reverse lookup - positions to symbols
	// note that 0 contains null or the empty element if there is one
	private Alphabet alphabet = new Alphabet();

	// the state machine. It can be one of StateMachine (without ranges)
	// or StateMachine1 (with ranges)
	private AbstractStateMachine sm;

	// when set, this flag indicates that the model has been compiled
	private boolean compiled = false;

	// when set, indicates that the functions followpos have been calculated
	private boolean functionsCalculated = false;

	// Internal flag indicating that the binary option for subtrees starting at AND
	// nodes
	// Subtrees starting at OR nodes do not need to be made binary (I changed the
	// algorithm for calculating the functions so it takes n-ary subtrees at OR
	// nodes)
	private static final boolean binary = false;

	// this will indicate the state.
	// since the initial state is 0, in the StateMachine, we start here with 1
	private int crtPosition = 1;

	/**
	 * associate an unique integer with each range object this is the current
	 * integer, and we start at 0
	 */
	private int crtRangePosition = 0;

	/**
	 * set for a syntax tree that has ranges used to chose the state machine and
	 * state machine generation algorithm
	 */
	private boolean hasRanges = false;

	/**
	 * Indicates that the content model contains an All operator (&). In this case
	 * the content model cannot be nested.
	 */
	private boolean isAllContentModel = false;

	// this maps pairs of ints indicating transitions, to range nodes, to which they
	// correspond
	// contains ranges that start and end on nodes represented by the pair of ints
	// used in generating the state machine with ranges (StataMachine2)
	private StatesPairsToRange statesPairToRange = new StatesPairsToRange();

	private Vector<OperatorRangeNode> rangeNodes = new Vector<OperatorRangeNode>();

	// this maps pairs end/start range states to the range they correspond to
	// it is used when generating the state machine with ranges (StateMachine2)
	// if two different ranges have the same pair of states, then the content
	// model is ambiguous
	// TODO: see if this extension to the notion of ambiguousness makes sense.
	@SuppressWarnings("serial")
	private class StatesPairsToRange extends HashMap<Pair<Integer, Integer>, ModelNode> {
		boolean add(Integer from, Integer to, OperatorRangeNode node) {
			Validate.notNull(node);

			Pair<Integer, Integer> ip = Pair.of(from, to);

			// if it contains this pair already as a key,
			// the content model is ambiguous
			return put(ip, node) == null;
		}

		OperatorRangeNode get(int from, int to) {
			return (OperatorRangeNode) get(Pair.of(from, to));
		}
	}

	/********************************************************
	 * the alphabet is a list of all symbols in the language recognized by the
	 * regular expression it is implemented as a vector where all the symbols are at
	 * the indexes indicated by their position (as defined by the algorithm)
	 *
	 * Note: It is assumed that the empty symbol (if there is at least one) is
	 * always at index 0 , and non-empty symbol start at 1.
	 ********************************************************/
	@SuppressWarnings("serial")
	private class Alphabet extends Vector<AbstractSymbolNode> {
		private int size = 0;
		private Set<String> uniqueSymbols = new HashSet<String>();

		// sets index 0 to null, to signal that initially, there was no empty element
		Alphabet() {
			addElement(null);
		}

		void addSymbol(AbstractSymbolNode symbolNode) {
			int index = symbolNode.getPosition();
			if (index >= size())
				setSize(index + 10);
			setElementAt(symbolNode, index);
			size = Math.max(size, index + 1);
			if (symbolNode.getValue().length() > 0)
				uniqueSymbols.add(symbolNode.getValue());
		}

		SymbolNode getSymbol(int pos) {
			return (SymbolNode) elementAt(pos);
		}

		Set<String> getUniqueSymbols() {
			return uniqueSymbols;
		}

		int getSize() {
			return size;
		}

		// indicates whether the empty symbol is in the alphabet
		boolean hasEmptySymbol() {
			return elementAt(0) != null;
		}

		public void dump(OutputStream os) throws IOException {
			String str = new String();
			str += "************ Alphabet ****************\n";
			str += "size: " + (new Integer(size)).toString() + "\n";
			for (int n = 0; n < size; n++) {
				AbstractSymbolNode node = (AbstractSymbolNode) elementAt(n);
				String str1 = null;
				if (node != null)
					str1 = node.getValue();
				str += str1 + " " + (new Integer(n)).toString() + "\n";
			}

			os.write(str.getBytes());
		}
	}

	/**************************************************
	 * Base class for all model tree nodes
	 **************************************************/
	protected static abstract class ModelNode extends NaryTree.Node<ModelNode> {
		private boolean nullable;
		// these sets are calculated by each node
		// Note: for some of the nodes, they are references to objects in other nodes,
		// when it is known that they are not distinct.
		// Care should be taken not to change these inadvertently, by silently changing
		// the referenced object.
		private IntegerSet firstpos = null;
		private IntegerSet lastpos = null;
		private IntegerSet internalpos = new IntegerSet();

		// used by clone only
		protected ModelNode(ModelNode node) {
			super(node);
		}

		protected ModelNode() {
		}

		protected ModelNode(String name) {
			super(name);
		}

		public String toString() {
			return super.toString();
		}

		final boolean nullable() {
			return nullable;
		}

		final IntegerSet firstpos() {
			return firstpos;
		}

		final IntegerSet lastpos() {
			return lastpos;
		}

		final IntegerSet internalpos() {
			return internalpos;
		}

		final protected void setFirstPos(IntegerSet fp) {
			firstpos = fp;
		}

		final protected void setLastPos(IntegerSet lp) {
			lastpos = lp;
		}

		final protected void setNullable(boolean _nullable) {
			nullable = _nullable;
		}

		// abstract void dumpExpression();
		void postCalculate() throws AmbiguousContentModelException {
			calculateInternalPos();
		}

		// calculates firstpos, lastpos and nullable for a node
		// default version doesn't do anything - the operator nodes will override this
		// method
		abstract void calculate();

		// calculates followpos
		void calculateFollowpos() {
		}

		abstract void calculateInternalPos();

		boolean isPosInternal(int pos) {
			return internalpos.contains(pos);
		}

		public void dump(OutputStream os) throws IOException {
			// super.dump();
			os.write((" Nullable: " + (new Boolean(nullable())).toString() + ", ").getBytes());
			if (firstpos() != null) {
				os.write(("Firstpos: ").getBytes());
				firstpos().dump(os);
				os.write((", ").getBytes());
			}
			if (lastpos() != null) {
				os.write(("Lastpos: ").getBytes());
				lastpos().dump(os);
				// ModelWriter.print( "," );
			}
		}

		// checks that a particular node can have any more children
		abstract boolean canHaveMoreChildren();
	}

	/**************************************************
	 * Operator node abstract class generic operator node class base class for the
	 * concrete operators classes
	 ***************************************************/
	protected static abstract class OperatorNode extends ModelNode {

		OperatorNode(OperatorNode node) {
			super(node);
		}

		OperatorNode(String name) {
			super(name);
		}

		public String toString() {
			return super.toString();
		}

		boolean isAmbiguous() {
			ModelNode node1 = (ModelNode) getFirstChild();
			ModelNode node2 = (ModelNode) getLastChild();

			return isAmbiguous(node1, node2);
		}

		protected abstract boolean isAmbiguous(ModelNode node1, ModelNode node2);

		// this actually only does something for AND
		// OR knows how to deal with n-ary, and other operators have one and only one
		// child
		OperatorNode makeBinary(OperatorNode node) {
			return node;
		}

		void calculateInternalPos() {
			for (ModelNode node = (ModelNode) getFirstChild(); node != null; node = (ModelNode) node.getNextSibling())
				internalpos().reunion(node.internalpos());
		}
	}

	static private abstract class UnaryOperatorNode extends OperatorNode {
		UnaryOperatorNode(String name) {
			super(name);
		}

		UnaryOperatorNode(UnaryOperatorNode node) {
			super(node);
		}

		// an unary operator cannot have more than 1 child
		boolean canHaveMoreChildren() {
			return getChildrenCount() == 0;
		}

		// an unary operator is valid if it has exactly one child
		public boolean check() {
			return getChildrenCount() == 1;
		}

		protected void calculateFirstPos(ModelNode node) {
			setFirstPos(node.firstpos());
		}

		protected void calculateLastPos(ModelNode node) {
			setLastPos(node.lastpos());
		}

		final void calculate() {
			ModelNode node = (ModelNode) getFirstChild();

			calculateNullable(node);
			calculateFirstPos(node);
			calculateLastPos(node);
		}

		protected abstract void calculateNullable(ModelNode node);
	}

	static private abstract class NaryOperatorNode extends OperatorNode {

		final void calculate() {
			ModelNode node1 = (ModelNode) getFirstChild();
			ModelNode node2 = (ModelNode) getLastChild();

			calculateNullable(node1, node2);
			calculateFirstPos(node1, node2);
			calculateLastPos(node1, node2);
		}

		protected abstract void calculateFirstPos(ModelNode node1, ModelNode node2);

		protected abstract void calculateLastPos(ModelNode node1, ModelNode node2);

		protected abstract void calculateNullable(ModelNode node1, ModelNode node2);

		NaryOperatorNode(String name) {
			super(name);
		}

		NaryOperatorNode(NaryOperatorNode node) {
			super(node);
		}

		// an nary operator can have more children
		boolean canHaveMoreChildren() {
			return true;
		}

		// an nary operator is valid if it has at least 2 children
		public boolean check() {
			return getChildrenCount() >= 2;
		}
	}

	/***********************************************
	 * Abstract base class for symbols and empty symbols
	 **********************************************/
	private static abstract class AbstractSymbolNode extends ModelNode {
		private int position = 0;
		protected String value = null;

		private AbstractSymbolNode(AbstractSymbolNode node) {
			super(node);
			value = node.getValue();
			position = node.getPosition();
		}

		protected AbstractSymbolNode(String _value, int _position) {
			super(_value);
			value = _value;
			position = _position;
		}

		String getValue() {
			return value;
		}

		public String toString() {
			return "\"" + value + "\"";
		}

		protected abstract IntegerSet followpos();

		int getPosition() {
			return position;
		}

		// a symbol node cannot have any more children
		boolean canHaveMoreChildren() {
			return false;
		}

		// a symbol node is valid if it doesn't have any children (it is a leaf in the
		// syntax tree)
		public boolean check() {
			return getChildrenCount() == 0;
		}

		boolean isOptional() {
			ModelNode node = (ModelNode) super.getParent();

			if (node != null) {
				/**
				 * @todo use polymorphism here and not instanceof. For the moment use this to
				 *       validate the ideas
				 */
				if (node instanceof OperatorQMarkNode)
					return true;
				else
					return false;
			} else
				return false;
		}

	}

	/**************************************************
	 * symbol node class
	 ***************************************************/
	private static class SymbolNode extends AbstractSymbolNode {
		private IntegerSet followpos = new IntegerSet();
		private IntegerSet startRanges = new IntegerSet();
		private IntegerSet endRanges = new IntegerSet();

		IntegerSet startRanges() {
			return startRanges;
		}

		IntegerSet endRanges() {
			return endRanges;
		}

		private SymbolNode(SymbolNode node) {
			super(node);
		}

		SymbolNode(String _value, int _pos) {
			super(_value, _pos);
		}

		public String toString() {
			return super.toString();
		}

		public ModelNode clone() {
			return new SymbolNode(this);
		}

		protected IntegerSet followpos() {
			return followpos;
		}

		boolean hasValue(String _value) {
			return value.equals(_value);
		}

		void calculate() {
			setFirstPos(new IntegerSet(getPosition()));
			setLastPos(new IntegerSet(getPosition()));
			setNullable(false);
		}

		public void dump(OutputStream os) throws IOException {
			os.write(("\"" + value + "\" " + (new Integer(getPosition())).toString() + " ").getBytes());
			os.write((" Followpos: ").getBytes());
			followpos.dump(os);
			super.dump(os);
		}

		void calculateInternalPos() {
			internalpos().add(getPosition());
		}

		void addEndRange(OperatorRangeNode node) {
			endRanges.add(node.getPos());
		}

		void addStartRange(OperatorRangeNode node) {
			startRanges.add(node.getPos());
		}
	}

	/***********************************************
	 * Empty symbol node class Objects of this class are created to model the empty
	 * input for example (a|<empty>) is the same as a?
	 **********************************************/
	private class EmptySymbolNode extends AbstractSymbolNode {
		private final static String empty = "";

		private EmptySymbolNode() {
			super(empty, 0);
		}

		EmptySymbolNode(EmptySymbolNode node) {
			super(node);
		}

		protected IntegerSet followpos() {
			return null;
		}

		public ModelNode clone() {
			return new EmptySymbolNode(this);
		}

		public void dump(OutputStream os) throws IOException {
			os.write(("<E> " + (new Integer(getPosition())).toString()).getBytes());
			super.dump(os);
		}

		public String toString() {
			return "<E>";
		}

		void calculate() {
			setNullable(true);
			setFirstPos(new IntegerSet());
			setLastPos(new IntegerSet());
		}

		void calculateInternalPos() {
		}
	}

	/***********************************************
	 * Operator Star node
	 **********************************************/
	static private class OperatorStarNode extends UnaryOperatorNode {
		Alphabet alphabet;

		OperatorStarNode(String name, Alphabet _alphabet) {
			super(name);
			alphabet = _alphabet;
		}

		OperatorStarNode(OperatorStarNode node) {
			super(node);
		}

		public void dump(OutputStream os) throws IOException {
			os.write(("*").getBytes());
			super.dump(os);
		}

		public String toString() {
			ModelNode node = (ModelNode) getFirstChild();
			String string = new String(node.toString());
			string += "*";
			return string;
		}

		// public String toString() { return "*"; }
		public OperatorStarNode clone() {
			return new OperatorStarNode(this);
		}

		// this operator only has a child, so node1 == node2
		protected void calculateNullable(ModelNode node) {
			setNullable(true);
		}

		void calculateFollowpos() {
			for (Integer n : lastpos()) {
				AbstractSymbolNode node = alphabet.elementAt(n);

				node.followpos().reunion(firstpos());
			}
		}

		protected boolean isAmbiguous(ModelNode node1, ModelNode node2) {
			return false;
		}
	}

	/***********************************************
	 * Operator Plus node
	 **********************************************/
	static private class OperatorPlusNode extends UnaryOperatorNode {
		Alphabet alphabet;

		OperatorPlusNode(String name, Alphabet _alphabet) {
			super(name);
			alphabet = _alphabet;
		}

		OperatorPlusNode(OperatorPlusNode node) {
			super(node);
		}

		public void dump(OutputStream os) throws IOException {
			os.write(("+").getBytes());
			super.dump(os);
		}

		public String toString() {
			String string = new String();
			ModelNode node = (ModelNode) getFirstChild();
			string = node.toString();
			string += "+";
			return string;
		}

		// public String toString() { return "+"; }
		public ModelNode clone() {
			return new OperatorPlusNode(this);
		}

		// this operator only has a child, so node1 == node2
		protected void calculateNullable(ModelNode node) {
			setNullable(node.nullable());
		}

		void calculateFollowpos() {
			for (Integer n : lastpos()) {
				AbstractSymbolNode node = alphabet.elementAt(n);

				node.followpos().reunion(firstpos());
			}
		}

		protected boolean isAmbiguous(ModelNode node1, ModelNode node2) {
			return false;
		}
	}

	/**************************************************
	 * Operator Range node class INFINITY is used to model an unlimited max value
	 ***************************************************/
	static private class OperatorRangeNode extends UnaryOperatorNode {
		private final Limits limits;
		private final int pos;
		private Alphabet alphabet;
		private StatesPairsToRange statesPairToRange;

		Limits getLimits() {
			return limits;
		}

		int getPos() {
			return pos;
		}

		OperatorRangeNode(OperatorRangeNode node) {
			super(node);
			pos = node.getPos();
			limits = node.getLimits();
		}

		OperatorRangeNode(String name, int _pos, Limits _limits, Alphabet _alphabet,
				StatesPairsToRange _statesPairToRange) throws BadLimitsException {
			super(name);
			pos = _pos;
			limits = _limits;
			alphabet = _alphabet;
			statesPairToRange = _statesPairToRange;
		}

		public void dump(OutputStream os) throws IOException {
			os.write(("Range " + limits.toString()).getBytes());
			super.dump(os);
		}

		public String toString() {
			ModelNode node = (ModelNode) getFirstChild();
			String string = new String(node.toString());
			return string + limits.toString();
		}

		public ModelNode clone() {
			return new OperatorRangeNode(this);
		}

		protected void calculateFirstPos(ModelNode child) {
			super.calculateFirstPos(child);
			// adds the current range to the map startrange state/range operator object
			for (Integer n : firstpos()) {
				SymbolNode node = (SymbolNode) alphabet.elementAt(n);
				node.addStartRange(this);
			}
		}

		protected void calculateLastPos(ModelNode child) {
			super.calculateLastPos(child);
			for (Integer n : lastpos()) {
				SymbolNode node = (SymbolNode) alphabet.elementAt(n);
				node.addEndRange(this);
			}
		}

		protected void calculateNullable(ModelNode node) {
			setNullable(limits.min() == 0 ? true : node.nullable());
		}

		// nullable: if min == 0, treat this as a "*", otherwise, treat as a "+"
		void calculateFollowpos() {
			for (Integer n : lastpos()) {
				SymbolNode node = (SymbolNode) alphabet.elementAt(n);
				node.followpos().reunion(firstpos());
			}

		}

		void postCalculate() throws AmbiguousContentModelException {
			// adding the range to the map associating transitions to ranges
			// it is OK to do it now (even if we should probably use a separate function
			// call)
			// because calculateFollowpos is always called after firstpos and lastpos are
			// calculated
			// and for this to work, the assumption is that we have firstpos and lastpos
			// TODO: put this in a separate function (make an implicit assumption explicit)
			for (Integer from : lastpos()) {
				for (Integer to : firstpos()) {
					if (!statesPairToRange.add(from, to, this))
						throw new AmbiguousContentModelException(
								((SymbolNode) alphabet.elementAt(from.intValue())).getValue(), "Ambiguous Ranges");
				}
			}
			super.postCalculate();
		}

		protected boolean isAmbiguous(ModelNode node1, ModelNode node2) {
			return false;
		}
	}

	/***********************************************
	 * Operator Question Mark node
	 **********************************************/
	static private class OperatorQMarkNode extends UnaryOperatorNode {
		OperatorQMarkNode(String name) {
			super(name);
		}

		OperatorQMarkNode(OperatorQMarkNode node) {
			super(node);
		}

		public String toString() {
			ModelNode node = (ModelNode) getFirstChild();
			String string = new String(node.toString());
			string += "?";
			return string;
		}

		public void dump(OutputStream os) throws IOException {
			os.write(("?").getBytes());
			super.dump(os);
		}

		// public String toString() { return "?"; }
		public ModelNode clone() {
			return new OperatorQMarkNode(this);
		}

		// this operator only has a child, so node1 == node2
		protected void calculateNullable(ModelNode node) {
			setNullable(true);
		}

		protected boolean isAmbiguous(ModelNode node1, ModelNode node2) {
			return false;
		}
	}

	static private class OperatorNeutralNode extends UnaryOperatorNode {
		OperatorNeutralNode(String name) {
			super(name);
		}

		OperatorNeutralNode(OperatorNeutralNode node) {
			super(node);
		}

		public String toString() {
			ModelNode node = (ModelNode) getFirstChild();
			return new String(node.toString());
		}

		public void dump(OutputStream os) throws IOException {
			os.write(("").getBytes());
			super.dump(os);
		}

		// public String toString() { return ""; }
		public ModelNode clone() {
			return new OperatorNeutralNode(this);
		}

		// this operator only has a child, so node1 == node2
		protected void calculateNullable(ModelNode node) {
			setNullable(node.nullable());
		}

		protected boolean isAmbiguous(ModelNode node1, ModelNode node2) {
			return false;
		}
	}

	/**************************************************
	 * Operator And node class
	 ***************************************************/
	static private class OperatorAndNode extends NaryOperatorNode {
		Alphabet alphabet;
		// used for showing the right expression
		boolean cloned = false;

		OperatorAndNode(String name, Alphabet _alphabet) {
			super(name);
			alphabet = _alphabet;
		}

		OperatorAndNode(OperatorAndNode node) {
			super(node);
			alphabet = node.getAlphabet();
			cloned = true;
		}

		public void dump(OutputStream os) throws IOException {
			os.write(("AND").getBytes());
			super.dump(os);
		}

		Alphabet getAlphabet() {
			return alphabet;
		}

		public String toString() {
			String string = new String();
			if (!cloned)
				string = "(";
			boolean first = true;
			for (ModelNode node = (ModelNode) getFirstChild(); node != null; node = (ModelNode) node.getNextSibling()) {
				if (!first)
					string += ",";
				else
					first = false;
				string += node.toString();
			}
			if (!cloned)
				string += ")";
			return string;
		}

		// public String toString() { return "AND"; }
		public ModelNode clone() {
			return new OperatorAndNode(this);
		}

		protected void calculateFirstPos(ModelNode node1, ModelNode node2) {
			if (binary) {
				if (node1.nullable())
					setFirstPos(((IntegerSet) node1.firstpos().clone()).reunion((IntegerSet) node2.firstpos()));
				else
					// do not clone, since it is the same
					// TODO: check that this assumption is correct at runtime
					setFirstPos((IntegerSet) node1.firstpos());
			} else {
				// this would be used if we accepted Nary Trees (as opposed to binary)
				ModelNode node = (ModelNode) getFirstChild();
				setFirstPos((IntegerSet) node.firstpos().clone());

				for (; node != null; node = (ModelNode) node.getNextSibling()) {
					ModelNode next = (ModelNode) node.getNextSibling();
					if (node.nullable() && next != null)
						firstpos().reunion(next.firstpos());
					else
						break;
				}
			}
		}

		protected void calculateLastPos(ModelNode node1, ModelNode node2) {
			if (binary) {
				if (node2.nullable())
					setLastPos(((IntegerSet) node2.lastpos().clone()).reunion((IntegerSet) node1.lastpos()));
				else
					// do not clone, since it is the same
					// TODO: check this assumption is correct at runtime, what if something else
					// changes the original set?
					setLastPos((IntegerSet) node2.lastpos());
			} else {
				ModelNode node = (ModelNode) getLastChild();
				setLastPos((IntegerSet) node.lastpos().clone());
				for (; node != null; node = (ModelNode) node.getPrevSibling()) {
					ModelNode prev = (ModelNode) node.getPrevSibling();
					if (node.nullable() && prev != null)
						lastpos().reunion(prev.lastpos());
					else
						break;
				}
			}
		}

		protected void calculateNullable(ModelNode node1, ModelNode node2) {
			if (binary)
				setNullable(node1.nullable() && node2.nullable());
			else {
				boolean b = true;
				for (ModelNode node = (ModelNode) getFirstChild(); node != null
						&& b; node = (ModelNode) node.getNextSibling())
					b = b && node.nullable();
				setNullable(b);
			}
		}

		void calculateFollowpos() {
			if (binary) {
				ModelNode node1 = (ModelNode) getFirstChild();
				ModelNode node2 = (ModelNode) getLastChild();

				for (Integer n : node1.lastpos()) {
					SymbolNode node = (SymbolNode) alphabet.elementAt(n);
					node.followpos().reunion(node2.firstpos());
				}
			} else {
				ModelNode node = (ModelNode) getFirstChild();

				IntegerSet fp1 = (IntegerSet) node.firstpos().clone();
				IntegerSet lp1 = (IntegerSet) node.lastpos().clone();
				boolean nl1 = node.nullable();

				for (node = (ModelNode) node.getNextSibling(); node != null; node = (ModelNode) node.getNextSibling()) {
					IntegerSet lp2 = node.lastpos();
					boolean nl2 = node.nullable();

					for (Integer n : lp1) {
						SymbolNode wnode = (SymbolNode) alphabet.elementAt(n);
						wnode.followpos().reunion(node.firstpos());
					}

					// calculate lastpos
					if (nl1)
						fp1 = fp1.reunion(lp2);

					// calculate firstpos
					if (nl2)
						lp1 = lp1.reunion(lp2);
					else
						lp1 = (IntegerSet) lp2.clone();
					// calculate nullable
					nl1 = nl1 && nl2;
				}
			}
		}

		// this makes AND nodes binary (groups AND so they don't have more than 2
		// children)
		OperatorNode makeBinary(OperatorNode node) {
			if (node.getChildrenCount() == 2) {
				// get last child, which will be moved down in the tree
				ModelNode node1 = (ModelNode) node.getLastChild();
				node = (OperatorNode) node1.insertParent((ModelNode) node.clone());
			}
			return node;
		}

		protected boolean isAmbiguous(ModelNode node1, ModelNode node2) {
			return false;
		}

	}

	/**************************************************
	 * Operator Or node class
	 ***************************************************/
	static private class OperatorOrNode extends NaryOperatorNode {
		OperatorOrNode(OperatorOrNode node) {
			super(node);
		}

		OperatorOrNode(String name) {
			super(name);
		}

		public void dump(OutputStream os) throws IOException {
			os.write(("OR").getBytes());
			super.dump(os);
		}

		public String toString() {
			String string = new String();
			string += "(";

			boolean first = true;
			for (ModelNode node = (ModelNode) getFirstChild(); node != null; node = (ModelNode) node.getNextSibling()) {
				if (!first)
					string += "|";
				else
					first = false;
				string += node.toString();
			}
			string += ")";
			return string;
		}

		// public String toString() { return "OR"; }
		public ModelNode clone() {
			return new OperatorOrNode("Cloned" + super.getName());
		}

		protected void calculateFirstPos(ModelNode node1, ModelNode node2) {
			ModelNode node = (ModelNode) getFirstChild();
			setFirstPos((IntegerSet) node.firstpos().clone());
			for (; node != null; node = (ModelNode) node.getNextSibling())
				firstpos().reunion(node.firstpos());
		}

		protected void calculateLastPos(ModelNode node1, ModelNode node2) {
			ModelNode node = (ModelNode) getFirstChild();
			setLastPos((IntegerSet) node.lastpos().clone());
			for (; node != null; node = (ModelNode) node.getNextSibling())
				lastpos().reunion(node.lastpos());
		}

		protected void calculateNullable(ModelNode node1, ModelNode node2) {
			boolean b = false;

			for (ModelNode node = (ModelNode) getFirstChild(); node != null; node = (ModelNode) node.getNextSibling())
				b = b || node.nullable();

			setNullable(b);
		}

		protected boolean isAmbiguous(ModelNode node1, ModelNode node2) {
			return false;
		}
	}

	/**
	 * Operator for All content model.
	 *
	 * This version does not support nested content models with All operators. This
	 * is consistent with XSDL which does not support this either.
	 *
	 * So a All operator needs to be a root, to have at least two children and all
	 * the children need to be of type AbstractSymbolNode i.e. symbols or leaf
	 * nodes.
	 */
	static private class OperatorAllNode extends NaryOperatorNode {
		OperatorAllNode(String name) {
			super(name);
		}

		OperatorAllNode(OperatorAllNode node) {
			super(node);
		}

		protected void calculateFirstPos(ModelNode node1, ModelNode node2) {
		}

		protected void calculateLastPos(ModelNode node1, ModelNode node2) {
		}

		protected void calculateNullable(ModelNode node1, ModelNode node2) {
		}

		protected boolean isAmbiguous(ModelNode node1, ModelNode node2) {
			return false;
		}

		public boolean check() {
			// for all content model, we only support optional elements at most. No nested
			// content models.
			// check all the children of the & operator
			for (ModelNode node = (ModelNode) getFirstChild(); node != null; node = (ModelNode) node.getNextSibling()) {
				if (!(node instanceof AbstractSymbolNode)) {
					// if a child is not a symbol node, it must be a ? node
					if (!(node instanceof OperatorQMarkNode))
						// if it is not a ? node, it is an error in the syntax tree
						return false;
					else {
						// if it is a ? node, it can only have a symbol child
						ModelNode node1 = (ModelNode) node.getFirstChild();
						if (!(node1 instanceof AbstractSymbolNode))
							// if it is not a symbol child, error
							return false;
					}
				}
			}

			if (!super.isRoot())
				return false;

			if (getChildrenCount() < 2)
				return false;

			return true;
		}

		public void dump(OutputStream os) throws IOException {
			os.write(("ALL").getBytes());
			super.dump(os);
		}

		public String toString() {
			String string = new String();
			string += "(";

			boolean first = true;
			for (ModelNode node = (ModelNode) getFirstChild(); node != null; node = (ModelNode) node.getNextSibling()) {
				if (!first)
					string += "&";
				else
					first = false;
				string += node.toString();
			}
			string += ")";
			return string;
		}

		public Node<ModelNode> clone() {
			return new OperatorAllNode(this);
		}

	}

	/**************************************************
	 * "copy" constructor
	 ***************************************************/
	// public NewModel( NewModel model ) { super( model ); name =
	// model.getName();/*TODO: more has to be done here: clone the alphabet, name
	// etc*/ }
	/**************************************************
	 * constructs a model with a name
	 ***************************************************/
	public SyntaxTree(String _name) {
		super();
		name = _name;
	}

	// public Object clone() { return new NewModel( this ); }
	String getName() {
		return name;
	}

	public String toString() {
		ModelNode root = (ModelNode) getRoot();
		if (root != null)
			return root.toString();
		else
			return new String();
	}

	// this will dump the content model expression in a readable form
	public void dumpExpression() {
		ModelWriter.println("----------------- Expression ----------------");
		ModelWriter.println("\"" + getName() + "\": " + toString());
		ModelWriter.println();
	}

	// dumps the content model expression, and the syntax tree
	public void dump() {
		ModelWriter.println("--------- Syntax tree and functions ---------");
		ModelWriter.print("Tree \"");
		ModelWriter.println(name + "\":");
		try {
			super.dump(ModelWriter.getOutputStream());
		} catch (IOException e) {
			System.err.println("IOException in SyntaxTree.dump: " + e.getMessage());
		}
	}

	/**************************************************
	 * compile - transforms the syntax tree into a deterministic state machine also
	 * determines the the content model ambiguousness
	 ***************************************************/
	public void compile() throws AmbiguousContentModelException {
		// only builds the DFA if it hasn't been built already
		if (!compiled) {
			// let's make sure that the syntax tree is valid
			Validate.isTrue(checkSyntaxTree((ModelNode) getRoot()));

			preprocessSyntaxTree();
			calculateFunctions();
			buildDFA();
			compiled = true;
		}
	}

	private boolean checkSyntaxTree(ModelNode root) {
		for (Node<ModelNode> n : getPostOrderIterator()) {
			ModelNode node = (ModelNode) n;
			if (!node.check())
				return false;
		}
		return true;
	}

	// any preprocessing of the syntax tree,
	// before starting the compilation process is done here
	private void preprocessSyntaxTree() {
		// for the moment, only do the transformation to
		// the star normal form
		makeStarNormalForm();
	}

	private void makeStarNormalForm() {
	}

	private void calculateFunctions() throws AmbiguousContentModelException {
		// first traversal of the syntax tree
		if (!this.isAllContentModel && !functionsCalculated) {
			// calculate only for content models that are not all (the all content model
			// generates a special type of "StateMachine"
			for (Node<ModelNode> n : getPostOrderIterator()) {
				ModelNode node = (ModelNode) n;
				node.calculate();
				node.calculateFollowpos();
				if (hasRanges)
					node.postCalculate();
			}
			functionsCalculated = true;
		}
	}

	/**************************************************
	 * generates the DFA using the Bruggeman algorithm determines the content model
	 * ambigousness
	 *
	 * The algorithm to calculate the transitions in the state machine
	 *
	 * For each symbol in the alphabet, whose position is "from" for all the
	 * positions in the followpos of each symbol if this position corresponds to
	 * some other symbol in the alphabet (whose pos is "to" ) than this is a
	 * transition on the initial symbol, from "from" to "to".
	 *
	 * Note: if there are two transitions on the same symbol from the same state,
	 * the content model is ambiguous
	 *
	 * In the case of ranges, the content model can be ambiguous if while building
	 * the state machine, we get two transitions on one range with increment, over
	 * the same states. (see the algorithm for ranges below)
	 **************************************************/
	private void buildDFA() throws AmbiguousContentModelException {
		// create a state machine with size = number of symbol nodes + 1 for the start
		// state
		// this works even for an empty syntax tree, for the state machine will only
		// have an initial state, which is also a final state.
		if (this.isAllContentModel) {
			// traverse the tree and add the symbols to the state machine
			makeStateMachineAll();
		} else {
			if (hasRanges)
				sm = new StateMachineRanges(name, alphabet.size() + 1, makeLimitsVector());
			else
				sm = new StateMachineNoRanges(name, alphabet.size() + 1);

			ModelNode root = (ModelNode) getRoot();
			if (root != null) {
				// if the syntax tree is non-empty
				calculateTransitions(root);
				calculateFinalNodes(root);
			}
		}
	}

	private void makeStateMachineAll() throws AmbiguousContentModelException {
		sm = new StateMachineAll();

		for (Node<ModelNode> n : getPostOrderIterator()) {
			ModelNode node = (ModelNode) n;
			AbstractSymbolNode symbolNode = (AbstractSymbolNode) node;
			String symbol = symbolNode.getValue();
			boolean optional = symbolNode.isOptional();
			if (!((StateMachineAll) sm).addTransition(symbol, optional))
				throw new AmbiguousContentModelException(symbol, "Duplicate symbol in all content model");

		}
	}

	private void calculateFinalNodes(ModelNode root) {
		// in case there is an empty symbol in the alphabet,
		// the first position is also a final position
		// TODO: this might not even be necessary (position 0 is probably already in the
		// lastpos set)
		if (root.nullable())
			root.lastpos().add(0);

		// set all the final positions
		if (hasRanges) {
			// if the state machine has ranges, final states are associated with
			// vectors of positions of ranges that have to be checked on output
			StateMachineRanges.FinalStates d = new StateMachineRanges.FinalStates();
			for (Integer i : root.lastpos()) {
				// if we are on the first node, no checking is necessary, because no edge can go
				// into the first node,
				// the first node in the final states is a result of having the root nullable,
				// which means that
				// we can exit the state machine as soon as we entered it.
				if (i.intValue() == 0)
					d.put(i, new IntegerVector());
				else {
					SymbolNode node = alphabet.getSymbol(i.intValue());
					d.put(i, new IntegerVector(node.endRanges()));
				}
			}
			sm.setFinalStates(d);
		} else
			sm.setFinalStates(root.lastpos());
	}

	private void calculateTransitions(int from, IntegerSet is) throws AmbiguousContentModelException {
		for (Integer to : is) {
			SymbolNode toSymbolNode = alphabet.getSymbol(to);

			// start the iteration at 1, to avoid the empty symbol which is (in case there
			// was
			// an empty symbol) at 0.
			// TODO: hide this assumption inside the class alphabet, by requesting an
			// iterator
			// on nonempty symbols
			Set<String> symbols = alphabet.getUniqueSymbols();
			for (String symbol : symbols) {
				if (toSymbolNode.hasValue(symbol)) {
					boolean result;
					if (hasRanges)
						result = calculateTransitionWithRanges(symbol, from, to);
					else
						// add transitions from the start state, which is 0
						result = ((StateMachineNoRanges) sm).addTransition(symbol, from, to);
					if (!result)
						throw new AmbiguousContentModelException(symbol, "Ambiguous content model");
				}
			}
		}
	}

	private boolean addTransitionToStartRange(String symbol, int from, int to, IntegerSet startRanges) {
		// the ends on a node where at least a range starts
		// get the vector of indexes of relevant ranges that start in this node
		// relevant meaning that even if ranges start there, not all of them
		// should be taken into account. This is calculated by getRelevantRangesVector
		StateMachineRanges sm2 = (StateMachineRanges) sm;
		IntegerVector v = getRelevantRangesVector(startRanges, from);

		if (v.size() == 0)
			return sm2.addValidTransition(symbol, from, to);
		else
			return sm2.addTransitionToStartRange(symbol, from, to, v);
	}

	private boolean addTransitionFromEndRange(String symbol, int from, int to, IntegerSet endRanges) {
		StateMachineRanges sm2 = (StateMachineRanges) sm;
		IntegerVector v = getRelevantRangesVector(endRanges, to);

		if (v.size() == 0)
			return sm2.addValidTransition(symbol, from, to);
		else
			return sm2.addTransitionFromEndRange(symbol, from, to, v);
	}

	private boolean addTransitionFromEndRangeToStartRange(String symbol, int from, int to, IntegerSet startRanges,
			IntegerSet endRanges) {
		StateMachineRanges sm2 = (StateMachineRanges) sm;

		OperatorRangeNode rangeNode = statesPairToRange.get(from, to);

		// if this transition ends on a start range node and starts in a end range node
		if (rangeNode != null) {
			// there is one range that starts in from and ends in to, which means that this
			// is
			// an increment count type of transition
			// this transition will increment the count for the range, and do the
			// usual stuff with the other ranges (check the terminating ranges, init the
			// starting ranges)
			int pos = rangeNode.getPos();
			// in this case, we pass "pos" to signal that the current range (going from
			// "from" to "to"
			// is no relevant in calculating the relevant ranges
			IntegerVector v1 = getRelevantRangesVector(startRanges, from, pos);
			IntegerVector v2 = getRelevantRangesVector(endRanges, to, pos);
			return sm2.addTransitionFromEndToStartRangeIncrement(symbol, from, to, v1, v2, pos);
		} else {
			//
			IntegerVector v1 = getRelevantRangesVector(startRanges, from);
			IntegerVector v2 = getRelevantRangesVector(endRanges, to);
			if (v1.size() == 0 && v2.size() == 0)
				return sm2.addValidTransition(symbol, from, to);
			else if (v1.size() != 0 && v2.size() == 0)
				return sm2.addTransitionToStartRange(symbol, from, to, v1);
			else if (v1.size() == 0 && v2.size() != 0)
				return sm2.addTransitionFromEndRange(symbol, from, to, v2);
			else
				return sm2.addTransitionFromEndToStartRangeNoIncrement(symbol, from, to, v1, v2);
		}
	}

	/**
	 * Generates transitions in case there are ranges in the content model
	 * expression
	 */
	private boolean calculateTransitionWithRanges(String symbol, int from, int to) {
		// dump();
		StateMachineRanges sm2 = (StateMachineRanges) sm;
		// the set of ranges ending in the "from" node
		IntegerSet endRanges;
		if (from != 0) {
			SymbolNode fromSymbolNode = alphabet.getSymbol(from);
			endRanges = new IntegerSet();
			if (fromSymbolNode != null)
				endRanges = fromSymbolNode.endRanges();
		} else
			// no range can start in the node 0
			endRanges = new IntegerSet();

		// the set of ranges starting in the "to" node
		IntegerSet startRanges = alphabet.getSymbol(to).startRanges();

		if (startRanges.size() == 0 && endRanges.size() == 0)
			// if this transition doesn't start or end on a transition
			// node, this is a regular valid transition
			return sm2.addValidTransition(symbol, from, to);
		else if (startRanges.size() != 0 && endRanges.size() == 0)
			// if this transition ends on a range start node,
			return addTransitionToStartRange(symbol, from, to, startRanges);
		else if (startRanges.size() == 0 && endRanges.size() != 0)
			// if this transition starts in a range end node
			return addTransitionFromEndRange(symbol, from, to, endRanges);
		else
			// if this transition starts and ends in range nodes
			return addTransitionFromEndRangeToStartRange(symbol, from, to, startRanges, endRanges);
	}

	// returns the indexes of relevant ranges, i.e. these ranges that have to be
	// somehow updated in the given context, on the transition
	// is - the set of indexes of ranges out of which we should find the relevant
	// ones
	// node -
	// pos - this will indicate to getRelevantVector what range should not be
	// considered when calculating the relevant ranges vector
	// by default it is -1, which means all ranges are considered
	private IntegerVector getRelevantRangesVector(IntegerSet is, int node, int pos) {
		IntegerVector v = new IntegerVector();

		// for all the positions in "is"
		for (Integer n : is) {
			// ignore pos. If pos == -1, than there is nothing to ignore
			if (n == pos)
				continue;
			// if the node is not internal to the range whose index is n, this is a relevant
			// node
			// this is because multiple transitions can correspond to the same range (in
			// case
			// there are optional elements, for example),
			// an example where this is needed:
			// range from 1 to 3. Transitions back from 3 to 1 and from 2 to 1. There is
			// also a transition
			// from 2 to 3. When calculating this last transitions, we would see 2 as an
			// ending of a range,
			// which in our case is not relevant because it is the same ending as 3, and so
			// when moving
			// from 2 to 3 we don't need to check 2 for range boundaries (2 is inside the
			// range boundaries)
			if (!((rangeNodes.elementAt(n)).isPosInternal(node)))
				v.addElement(new Integer(n));
		}
		return v;
	}

	private IntegerVector getRelevantRangesVector(IntegerSet is, int node) {
		return getRelevantRangesVector(is, node, -1);
	}

	// this algorithm calculates the transitions from the functions
	// nullable, lastpos, firstpos and followpos
	private void calculateTransitions(ModelNode root) throws AmbiguousContentModelException {

		// calculate transition from start node (0 doesn't really correspond to a symbol
		// node,
		// so handle it separately
		IntegerSet is = root.firstpos();
		calculateTransitions(0, is);

		// calculate all the other transitions
		for (int from = 1; from < (alphabet.getSize()); from++) {
			SymbolNode wn = (SymbolNode) alphabet.elementAt(from);
			IntegerSet followpos = wn.followpos();
			// IntegerSet followpos = ( ( SymbolNode)symbolNodes.elementAt( from )
			// ).followpos();
			calculateTransitions(from, followpos);
		}
	}

	OperatorStarNode makeStarNode(String name) {
		return new OperatorStarNode(name, alphabet);
	}

	OperatorPlusNode makePlusNode(String name) {
		return new OperatorPlusNode(name, alphabet);
	}

	OperatorQMarkNode makeOptNode(String name) {
		return new OperatorQMarkNode(name);
	}

	OperatorNeutralNode makeNeutralNode(String name) {
		return new OperatorNeutralNode(name);
	}

	OperatorOrNode makeOrNode(String name) {
		return new OperatorOrNode(name);
	}

	OperatorAndNode makeAndNode(String name) {
		return new OperatorAndNode(name, alphabet);
	}

	OperatorAllNode makeAllNode(String name) {
		isAllContentModel = true;
		return new OperatorAllNode(name);
	}

	OperatorRangeNode makeRangeNode(String name, int min, int max) throws BadLimitsException {
		OperatorRangeNode node = new OperatorRangeNode(name, crtRangePosition++, new Limits(min, max), alphabet,
				statesPairToRange);
		// add the range node to the range nodes vector
		rangeNodes.addElement(node);
		hasRanges = true;
		return node;

	}

	AbstractSymbolNode makeSymbolNode(String name) {
		Validate.notNull(name);

		AbstractSymbolNode node;
		if (name.length() == 0)
			node = new EmptySymbolNode();
		else
			node = new SymbolNode(name, crtPosition++);

		alphabet.addSymbol(node);
		return node;
	}

	public AbstractState getInitialState() {
		return sm.getInitialState();
	}

	private LimitsVector makeLimitsVector() {
		LimitsVector lv = new LimitsVector();

		for (OperatorRangeNode node : rangeNodes) {
			lv.addElement(node.getLimits());
		}
		return lv;
	}

	boolean isBinary() {
		return binary;
	}

	ModelNode addNode(ModelNode parent, ModelNode node) {
		Validate.notNull(node);
		if (parent != null)
			Validate.isInstanceOf(OperatorNode.class, parent, "Can add children only to operators");

		if (parent != null)
			// check that unary operators don't have already children
			Validate.isTrue(((ModelNode) parent).canHaveMoreChildren(), "Unary operators can only have one child");

		OperatorNode op = (OperatorNode) parent;

		if (op == null)
			// if the tree is empty
			return (ModelNode) setRoot(node);
		else {
			// if we decide to make the tree binary
			return (ModelNode) op.addChildToBack(node);
		}
	}

	public void dumpStateMachine() {
		if (sm != null) {
			sm.dump();
		}
	}

	public boolean isCompiled() {
		return compiled;
	}

	public Set<String> getAlphabet() {
		return alphabet.getUniqueSymbols();
	}
}