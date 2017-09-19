/*
 * Copyright (c) 2017, Adrian Michel
 * http://www.amichel.com
 * 
 * This software is released under the 3-Clause BSD License
 * 
 * The complete terms can be found in the attached LICENSE file 
 * or at https://opensource.org/licenses/BSD-3-Clause 
*/

package com.amichel.collections;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import org.apache.commons.lang3.Validate;

import com.amichel.collections.NaryTree.Node;

/**
 * Implements a n-ary tree, i.e. a tree whose nodes can have any number of
 * children. Not thread safe
 */
public class NaryTree<T extends Node<T>> {
	/***************************************************
	 * private data
	 **************************************************/
	// the tree has a root
	private Node<T> root = null;

	/**
	 * Node - a node in the tree A node has a name, which does not have any special
	 * meaning - it can be used for debugging or other purposes
	 *
	 * The traversal of a level in the tree can only be done sequentially - random
	 * access in the to the nodes is not possible. Each node has links to all the
	 * Surrounding nodes: parent, left, right, first child, last child
	 */
	public static abstract class Node<T> {

		// a node has a name and...
		private String name = null;
		// a parent and ...
		private Node<T> parent = null;
		// a left sibling and ...
		private Node<T> prev = null;
		// a right sibling and ...
		private Node<T> next = null;
		// children
		private Node<T> firstChild = null;
		private Node<T> lastChild = null;

		/**
		 * This is to be used in case where a class that is also a node cannot actually
		 * be implemented as extending Node because Java doesn't allow multiple
		 * inheritance, and instead contains a node as a way of being still a node in a
		 * tree. Then, the nodeObject is used to keep a link between the "abstract" node
		 * and the actual object that is supposed to be the concrete node In order to
		 * still have strong typing, a class extending Node can be defined to do the
		 * appropriate casts
		 */
		private T nodeObject = null;

		// the number of children a node has
		private int childrenCount = 0;

		/**
		 * Constructs a node with a name
		 *
		 * @param _name
		 *            The name of the node
		 */
		public Node(String name) {
			this.name = name;
		}

		/**
		 * Default constructor - constructs a node with an empty name.
		 */
		public Node() {
			name = null;
		}

		/**
		 * copy constructor - only copies the name of another node.
		 *
		 * Note: to see what the sense of this is. It may not be used at all.
		 *
		 * @param node
		 *            The node to be copied
		 */
		public Node(Node<T> node) {
			name = node.getName();
		}

		/**
		 * Constructs a node with a name and a node object
		 *
		 * @param _name
		 *            The name of the node
		 * @param nodeObject
		 *            The node object associated with the node
		 */
		public Node(String name, T nodeObject) {
			this.nodeObject = nodeObject;
			this.name = name;
		}

		/**
		 * Constructs a node from a node object
		 *
		 * @param nodeObject
		 *            The node object to be associated with this node
		 */
		public Node(T nodeObject) {
			this.nodeObject = nodeObject;
		}

		/**
		 * Gets the parent node of the current node
		 *
		 * @return The parent node or null if this is the root node
		 */
		public final Node<T> getParent() {
			return parent;
		}

		/**
		 * Sets the parent of the current node
		 *
		 * @param _parent
		 *            The new parent node
		 */
		final void setParent(Node<T> parent) {
			this.parent = parent;
		}

		/**
		 * Sets the node next to the current node
		 *
		 * @param node
		 *            The node to become the next node
		 */
		final void setNext(Node<T> node) {
			next = node;
		}

		/**
		 * Sets the node to be the new previous node to the current node
		 *
		 * @param node
		 *            The node to become the previous node
		 */
		final void setPrev(Node<T> node) {
			prev = node;
		}

		/**
		 * Sets the last child of the current node
		 *
		 * @param node
		 *            The node to become the last child of the current node
		 */
		final void setLastChild(Node<T> node) {
			lastChild = node;
		}

		/**
		 * Sets the node to become the first child to the current node
		 *
		 * @param node
		 *            The node to become the first child
		 */
		final void setFirstChild(Node<T> node) {
			firstChild = node;
		}

		/**
		 * Retrieves the first child of the current node
		 *
		 * @return The first child
		 */
		public final Node<T> getFirstChild() {
			return firstChild;
		}

		/**
		 * Retrieves the last child of the current node
		 *
		 * @return The last child
		 */
		public final Node<T> getLastChild() {
			return lastChild;
		}

		/**
		 * The name of the current node
		 *
		 * @return The name
		 */
		final protected String getName() {
			return name;
		}

		/**
		 * Indicates if the current node is root (it does not have any parent)
		 *
		 * @return true if the node is root, false otherwise
		 */
		public final boolean isRoot() {
			return parent == null;
		}

		/**
		 * Retrieves the node object associated with the current node
		 *
		 * @return The node object
		 */
		final T getNodeObject() {
			return nodeObject;
		}

		/**
		 * Adds a child node to the end of the children list
		 *
		 * @param node
		 *            The node to be added
		 * @return The node added to the back
		 */
		public final Node<T> addChildToBack(Node<T> node) {
			Validate.notNull(node);

			// set the new child parent link
			node.setParent(this);

			// adding the child to the end of the list
			if (firstChild == null)
				firstChild = lastChild = node;
			else {
				lastChild.setNext(node);
				node.setPrev(lastChild);
			}
			lastChild = node;

			childrenCount++;

			return node;
		}

		/**
		 * Adds a child node to the front of the children list
		 *
		 * @param node
		 *            The node to be added to the front
		 * @return The node added to the front
		 */
		final Node<T> addChildToFront(Node<T> node) {
			Validate.notNull(node);

			// set the new child parent link
			node.setParent(this);

			// adding the child to the front of the list
			if (firstChild == null)
				firstChild = lastChild = node;
			else {
				firstChild.setPrev(node);
				node.setNext(firstChild);
			}
			firstChild = node;

			childrenCount++;

			return node;
		}

		/**
		 * Adds a node to the front of the siblings list on the same level as the
		 * current node.
		 *
		 * @param sibling
		 *            The new sibling to be added
		 * @return The new sibling added
		 */
		final Node<T> addSiblingToFront(Node<T> sibling) {
			Validate.notNull(parent, "parent of current node is null, so cannot add sibling");
			Validate.notNull(sibling, "the sibling to be added is null, cannot add a null sibling");

			return parent.addChildToFront(sibling);
		}

		/**
		 * Adds a node to the back of the siblings list on the same level as the current
		 * node.
		 *
		 * @param sibling
		 *            The new sibling to be added
		 * @return The new sibling added
		 */
		public final Node<T> addSiblingToBack(Node<T> sibling) {
			Validate.notNull(parent, "parent of current node is null, so cannot add sibling");
			Validate.notNull(sibling, "the sibling to be added is null, cannot add a null sibling");

			return parent.addChildToBack(sibling);
		}

		/**
		 * Inserts a parent in the children list between the current node and its old
		 * parent. The current node is added to the end of the list of children nodes of
		 * the new parent (in case the new parent already has children)
		 *
		 * The current node becomes child of the new parent and the new parent becomes
		 * child of the old parent of the current node
		 *
		 * @param newParent
		 *            The new parent to be inserted
		 * @return The node inserted as parent
		 */
		public final Node<T> insertParent(Node<T> newParent) {
			Validate.notNull(newParent);

			// this is root in the tree

			Node<T> oldParent = parent;

			// set the new parent for the current node
			parent = newParent;
			// position the new parent in the list of siblings
			newParent.setPrev(prev);
			newParent.setNext(next);
			if (prev != null)
				prev.setNext(newParent);
			else if (oldParent != null)
				oldParent.setFirstChild(newParent);

			if (next != null)
				next.setPrev(newParent);
			else if (oldParent != null)
				oldParent.setLastChild(newParent);

			// set the parent of the newParent to oldParent
			newParent.setParent(oldParent);
			// add the current node as a child of the new parent
			newParent.addChildToBack(this);
			// set lastchild/firstchild of the old parent, in case
			// the new parent is either of them

			return newParent;
		}

		/**
		 * Inserts a parent in the children list between the current node and its old
		 * parent. The current node is added to the front of the list of children nodes
		 * of the new parent (in case the new parent already has children)
		 *
		 * The current node becomes child of the new parent and the new parent becomes
		 * child of the old parent of the current node
		 *
		 * @param newParent
		 *            The new parent to be inserted
		 * @return The node inserted as parent
		 */
		final Node<T> insertParentToFront(Node<T> newParent) {
			Validate.notNull(newParent);

			// this is root in the tree

			Node<T> oldParent = parent;

			// set the new parent for the current node
			parent = newParent;
			// position the new parent in the list of siblings
			newParent.setPrev(prev);
			newParent.setNext(next);
			if (prev != null)
				prev.setNext(newParent);
			else if (oldParent != null)
				oldParent.setFirstChild(newParent);

			if (next != null)
				next.setPrev(newParent);
			else if (oldParent != null)
				oldParent.setLastChild(newParent);

			// set the parent of the newParent to oldParent
			newParent.setParent(oldParent);
			// add the current node as a child of the new parent
			newParent.addChildToFront(this);
			// set lastchild/firstchild of the old parent, in case
			// the new parent is either of them

			return newParent;
		}

		/**
		 * Generate a string with as many spaces as the indentation level and writes it
		 * to the output stream.
		 *
		 * Used when dumping the tree, in order to show the indentation of different
		 * levels.
		 *
		 * @param os
		 *            The output stream
		 * @param n
		 *            The indentation level
		 * @exception IOException
		 */
		protected void Indent(OutputStream os, int n) throws IOException {
			String str = new String();
			while (n-- > 0)
				str += "  ";
			os.write(str.getBytes());

		}

		/**
		 * Indicates if the current node is a leaf in the tree i.e. it does not have any
		 * children
		 *
		 * @return true if the current node is a leaf, false otherwise
		 */
		boolean isLeaf() {
			return childrenCount == 0;
		}

		/**
		 * Retrieves the next sibling node of the current node
		 *
		 * @return The next sibling or null if there is none
		 */
		public final Node<T> getNextSibling() {
			return next;
		}

		/**
		 * Get the previous sibling node of the current node
		 *
		 * @return The previous sibling node or null if there is none
		 */
		public final Node<T> getPrevSibling() {
			return prev;
		}

		/**
		 * Retrieves the number of children nodes of the current node
		 *
		 * @return The number of children
		 */
		public final int getChildrenCount() {
			return childrenCount;
		}

		/**
		 * returns the number of siblings on the level of the current node, including
		 * itself.
		 *
		 * @return The number of siblings
		 */
		final int getSiblingsCount() {
			if (parent != null)
				return parent.getChildrenCount();
			else
				// there is only one root
				return 1;
		}

		/**
		 * Dumps the contents of the node to the output stream as a string, considering
		 * the indent level
		 *
		 * @param os
		 *            The output stream
		 * @param n
		 *            The indentation level.
		 * @exception IOException
		 */
		public void dump(OutputStream os, int n) throws IOException {
			Indent(os, n);
			dump(os);
			os.write(("\n").getBytes());
		}

		/**
		 * Sends the name of the current node to the output stream
		 *
		 * @param os
		 *            The output stream
		 * @exception IOException
		 */
		public void dump(OutputStream os) throws IOException {
			os.write(name.getBytes());
		}

		/**
		 * Clones the current node.
		 *
		 * @return A clone of the current node
		 */
		public abstract Node<T> clone();

		// checks that a particular node is valid
		public abstract boolean check();
	}

	/**
	 * enumerator for post order traversal of the tree More efficient that recursive
	 * traversal
	 *
	 * Note: if the tree is changed while traversing it, objects of this class
	 * become invalid TODO: should I enforce this, or should I assume that the
	 * programmer follows this rule?
	 */
	public static class PostOrderIterator<T extends Node<T>> implements Iterable<Node<T>> {
		/**
		 * The current node
		 */
		private Node<T> crtNode;
		/**
		 * Indicates if the subtree starting at crtNode was traversed or not
		 */
		private boolean traversed;

		/**
		 * Constructs a PostOrderEnumerator for a tree initialized to the root of the
		 * tree
		 *
		 * @param tree
		 *            The tree whose enumerator we need
		 */
		PostOrderIterator(NaryTree<T> tree) {
			crtNode = tree.getRoot();
			traversed = false;
		}

		/**
		 * Indicates if there are more nodes or not in the iterator
		 *
		 * @return true if there are more nodes in the iterator, false if there are no
		 *         more nodes
		 */
		public boolean hasMoreElements() {
			return crtNode != null;
		}

		/**
		 * Retrieves the next element in the iterator
		 *
		 * @return The next node in the iterator or null if there are no more nodes or
		 *         if the tree is empty
		 */
		public T nextElement() {
			Node<T> retNode;
			Node<T> node;

			// if the crtNode == null, no more elements
			if (crtNode == null)
				return null;

			if (traversed) {
				// if we traversed the subtree starting at the current node
				// next element is the current node
				retNode = crtNode;
				// calculate the next current node
				crtNode = nextCrtNode(crtNode);
			} else {
				// if the subtree hasn't been traversed, find the first leaf
				node = findFirstLeaf(crtNode);
				if (node != null) {
					// next node is this first leaf
					retNode = node;
					// calculate the next current node
					crtNode = nextCrtNode(node);
				} else {
					// if no leaf, next node is current node
					retNode = crtNode;
					// calculate the next current node
					crtNode = nextCrtNode(node);
				}
			}
			return (T) retNode;
		}

		/**
		 * calculate the next current node
		 *
		 * @param node
		 *            The present current node
		 * @return The newly calculated current node, or null if there are no more nodes
		 *         or if the tree is empty
		 */
		private Node<T> nextCrtNode(Node<T> node) {
			Node<T> node1 = node.getNextSibling();

			if (node1 == null) {
				// if node doesn't have any siblings
				// the current node is its parent,
				// also set the parent as traversed,
				// since we are coming from the child
				traversed = true;
				return node.getParent();
			} else {
				// if it has sibling, set the current node to it
				// also, since it is a new node, set traversed to false
				traversed = false;
				return node1;
			}
		}

		/**
		 * Finds the first leaf for the subtree starting at node
		 *
		 * @param node
		 *            The root of the subnode
		 * @return The first leaf or null if there are no more
		 */
		private Node<T> findFirstLeaf(Node<T> node) {
			if (node != null)
				while (!node.isLeaf())
					node = node.getFirstChild();
			return node;
		}

		@Override
		public Iterator<Node<T>> iterator() {
			Iterator<Node<T>> it = new Iterator<Node<T>>() {

				@Override
				public boolean hasNext() {
					return hasMoreElements();
				}

				@Override
				public T next() {
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

	/**
	 * "Copy" constructor. Creates a tree whose nodes and structure is a copy of
	 * another tree
	 *
	 * @param tree
	 *            The source tree to be cloned
	 */
	public NaryTree(NaryTree<T> tree) {
		Validate.notNull(tree);

		Node<T> root = tree.getRoot();
		root = root.clone();
		setRoot(root);

		cloneChildren(getRoot(), tree.getRoot());
	}

	/**
	 * Default constructor. Constructs an empty tree
	 */
	public NaryTree() {
	}

	/**
	 * Adds a child to the back of the list of nodes of the parent
	 *
	 * @param parent
	 *            The parent node
	 * @param child
	 *            The new child to be added
	 * @return The new child
	 */
	final public Node<T> addChildToBack(Node<T> parent, Node<T> child) {
		Validate.notNull(parent);
		Validate.notNull(child);

		return parent.addChildToBack(child);
	}

	/**
	 * Adds a sibling to the back of the list of siblings of the node
	 *
	 * @param node
	 *            The node on whose list of siblings the new sibling will be added
	 * @param sibling
	 * @return The new sibling node
	 */
	final public Node<T> addSiblingToBack(Node<T> node, Node<T> sibling) {
		Validate.notNull(node);
		Validate.notNull(sibling);

		return node.addSiblingToBack(sibling);
	}

	/**
	 * Adds a child to the front of the list of children of the node
	 *
	 * @param parent
	 *            The parent node
	 * @param child
	 *            The new child node
	 * @return The new child node
	 */
	final public Node<T> addChildToFront(Node<T> parent, Node<T> child) {
		Validate.notNull(parent);
		Validate.notNull(child);

		return parent.addChildToFront(child);
	}

	/**
	 * Adds a sibling to the back of the list of siblings of the node
	 *
	 * @param node
	 *            The node on whose list of siblings the new sibling will be added
	 * @param sibling
	 *            The new sibling
	 * @return The new sibling
	 */
	final public Node<T> addSiblingToFront(Node<T> node, Node<T> sibling) {
		Validate.notNull(node);
		Validate.notNull(sibling);

		return node.addSiblingToFront(sibling);
	}

	final public boolean isRoot(Node<T> node) {
		return node == root;
	}

	/**
	 * Adds a subtree to the current tree by making the root of the subtree a child
	 * of the parent node. The subtree is added at the end of the list of children
	 *
	 * @param parent
	 *            The parent of the new subtree
	 * @param child
	 *            The tree to be added as a subtree to the current tree
	 * @return The current tree
	 */
	public NaryTree<T> addChildTreeToBack(Node<T> parent, NaryTree<T> child) {
		Validate.notNull(child);

		Validate.isTrue(parent != null || (parent == null && root == null));

		if (parent == null)
			root = child.getRoot();
		else
			parent.addChildToBack(child.getRoot());
		return this;
	}

	/**
	 * Adds a subtree to the current tree by making the root of the subtree a
	 * sibling of the node. The subtree is added at the end of the list of siblings
	 * of the node.
	 *
	 * @param node
	 *            The node whose sibling the new tree will become
	 * @param sibling
	 *            The tree to become a subtree
	 * @return The current tree
	 */
	public NaryTree<T> addSiblingTreeToBack(Node<T> node, NaryTree<T> sibling) {
		Validate.notNull(node);
		Validate.notNull(sibling);

		node.addSiblingToBack(sibling.getRoot());
		return this;
	}

	/**
	 * Inserts a new parent between the node and its current parent.
	 *
	 * @param node
	 *            The node whose parent is to be changed
	 * @param parent
	 *            The new parent node
	 * @return The node whose parent was changed
	 * @see Node.insertParentToFront
	 */
	final public Node<T> insertParentToFront(Node<T> node, Node<T> parent) {
		// if we are inserting at root, newParent becomes root
		if (isRoot(node)) {
			setRoot(parent);
			if (parent != null)
				parent.addChildToFront(node);
		} else
			node.insertParentToFront(parent);
		return node;
	}

	/**
	 * Inserts a new parent between the node and its current parent.
	 *
	 * @param node
	 *            The node whose parent is to be changed
	 * @param parent
	 *            The new parent node
	 * @return The node whose parent was changed
	 * @see Node.insertParentToBack
	 */
	final public Node<T> insertParentToBack(Node<T> node, Node<T> parent) {
		// if we are inserting at root, newParent becomes root
		if (isRoot(node)) {
			setRoot(parent);
			if (node != null)
				parent.addChildToBack(node);
		} else
			node.insertParent(parent);
		return node;
	}

	/**
	 * Retrieves the parent of a node
	 *
	 * @param node
	 *            The node whose parent is to retrieved
	 * @return The parent node or null if node is the root
	 */
	final public Node<T> getParent(Node<T> node) {
		return node.getParent();
	}

	/**
	 * Retrieves the root node of the current tree
	 *
	 * @return The root node or null if the tree is empty
	 */
	final public Node<T> getRoot() {
		return root;
	}

	/**
	 * Sets the root node of the current tree
	 *
	 * @param _root
	 *            The new root.
	 *
	 *            Note: the new root cannot be null
	 * TODO throw exception in non debug mode if the node is null
	 * @return The new root node
	 */
	final public Node<T> setRoot(Node<T> root) {
		Validate.notNull(root);
		return this.root = root;
	}

	/**
	 * Retrieves the next sibling of a node
	 *
	 * @param node
	 *            The node whose sibling is to be retrieved
	 * @return The next sibling or null if there is no next sibling
	 */
	final public Node<T> getNextSibling(Node<T> node) {
		Validate.notNull(node);
		return node.getNextSibling();
	}

	/**
	 * Retrieves the previous sibling of a node
	 *
	 * @param node
	 *            The node whose sibling is to be retrieved
	 * @return The previous sibling or null if there is no previous sibling
	 */
	final public Node<T> getPrevSibling(Node<T> node) {
		Validate.notNull(node);
		return node.getPrevSibling();
	}

	/**
	 * Clones a subtree starting at nodeFrom and attaches it as a subtree of nodeTo
	 *
	 * @param nodeTo
	 *            The destination node
	 * @param nodeFrom
	 *            The root of the source subtree
	 */
	private void cloneChildren(Node<T> nodeTo, Node<T> nodeFrom) {
		Validate.notNull(nodeTo);
		Validate.notNull(nodeFrom);

		for (Node<T> node = nodeFrom.getFirstChild(); node != null; node = node.getNextSibling()) {
			Node<T> newNode = node.clone();
			nodeTo.addChildToBack(newNode);
			cloneChildren(newNode, node);
		}
	}

	/**
	 * Retrieves an iterator object, whose implementation is a PostOrderIterator
	 *
	 * @return The Iterator object
	 */
	public PostOrderIterator<T> getPostOrderIterator() {
		return new PostOrderIterator<T>(this);
	}

	/**
	 * Dumps the content of the tree to the output stream
	 *
	 * @param os
	 *            Output stream
	 * @exception IOException
	 */
	public void dump(OutputStream os) throws IOException {
		if (root != null)
			dump(os, root, 0);
		/* else tree empty */}

	/**
	 * Dumps a subtree of the current tree whose root is node, to the output stream
	 *
	 * @param os
	 *            Output stream
	 * @param node
	 *            The root of the subtree to be dumped
	 * @param n
	 *            The indentation level, based on the level in the tree
	 * @exception IOException
	 */
	private void dump(OutputStream os, Node<T> node, int n) throws IOException {
		node.dump(os, n);

		for (Node<T> child = node.getFirstChild(); child != null; child = child.getNextSibling())
			dump(os, child, n + 1);
	}
}
