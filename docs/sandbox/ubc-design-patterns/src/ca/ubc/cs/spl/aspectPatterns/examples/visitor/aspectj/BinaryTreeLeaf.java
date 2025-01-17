package ca.ubc.cs.spl.aspectPatterns.examples.visitor.aspectj;

/* -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This file is part of the design patterns project at UBC
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * either https://www.mozilla.org/MPL/ or https://aspectj.org/MPL/.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is ca.ubc.cs.spl.aspectPatterns.
 *
 * For more details and the latest version of this code, please see:
 * https://www.cs.ubc.ca/labs/spl/projects/aodps.html
 *
 * Contributor(s):
 */

/**
 * Implements a <i>ConcreteElement</i> of the aggregate strcuture. This is a
 * terminal binary tree element (leaf).
 *
 * @author  Jan Hannemann
 * @author  Gregor Kiczales
 * @version 1.1, 02/17/04
 */

public class BinaryTreeLeaf implements Visitable {

    /**
     * the value stored in this leaf
     */

	protected int value;

    /**
     * Creates a new Leaf with the given value.
     *
     * @param value the value of the leaf
     */

	public BinaryTreeLeaf(int value) {
		this.value = value;
	}

	/**
	 * Accessor for the leaf's value
	 *
	 * @return the leaf's value
	 */

	public int getValue() {
	    return value;
	}
}
