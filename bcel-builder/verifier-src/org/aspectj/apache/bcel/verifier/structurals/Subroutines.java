package org.aspectj.apache.bcel.verifier.structurals;

/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (https://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache BCEL" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache BCEL", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <https://www.apache.org/>.
 */

import org.aspectj.apache.bcel.Constants;
import org.aspectj.apache.bcel.generic.*;
import org.aspectj.apache.bcel.verifier.exc.*;
import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;

	/**
	 * Instances of this class contain information about the subroutines
	 * found in a code array of a method.
	 * This implementation considers the top-level (the instructions
	 * reachable without a JSR or JSR_W starting off from the first
	 * instruction in a code array of a method) being a special subroutine;
	 * see getTopLevel() for that.
	 * Please note that the definition of subroutines in the Java Virtual
	 * Machine Specification, Second Edition is somewhat incomplete.
	 * Therefore, JustIce uses an own, more rigid notion.
	 * Basically, a subroutine is a piece of code that starts at the target
	 * of a JSR of JSR_W instruction and ends at a corresponding RET
	 * instruction. Note also that the control flow of a subroutine
	 * may be complex and non-linear; and that subroutines may be nested.
	 * JustIce also mandates subroutines not to be protected by exception
	 * handling code (for the sake of control flow predictability).
	 * To understand JustIce's notion of subroutines, please read
   *
	 * TODO: refer to the paper.
	 *
	 * @version $Id: Subroutines.java,v 1.4 2009/09/09 19:56:20 aclement Exp $
	 * @author <A HREF="https://www.inf.fu-berlin.de/~ehaase"/>Enver Haase</A>
	 * @see #getTopLevel()
	 */
public class Subroutines{
	/**
	 * This inner class implements the Subroutine interface.
	 */
	private class SubroutineImpl implements Subroutine{
		/**
		 * UNSET, a symbol for an uninitialized localVariable
		 * field. This is used for the "top-level" Subroutine;
		 * i.e. no subroutine.
		 */
		private final static int UNSET = -1;

		/**
		 * The Local Variable slot where the first
		 * instruction of this subroutine (an ASTORE) stores
		 * the JsrInstruction's ReturnAddress in and
		 * the RET of this subroutine operates on.
		 */
		private int localVariable = UNSET;

		/** The instructions that belong to this subroutine. */
		private HashSet<Serializable> instructions = new HashSet<Serializable>(); // Elements: InstructionHandle

		/*
		 * Refer to the Subroutine interface for documentation.
		 */
		public boolean contains(InstructionHandle inst){
			return instructions.contains(inst);
		}

		/**
		 * The JSR or JSR_W instructions that define this
		 * subroutine by targeting it.
		 */
		private HashSet<InstructionHandle> theJSRs = new HashSet<InstructionHandle>();

		/**
		 * The RET instruction that leaves this subroutine.
		 */
		private InstructionHandle theRET;

		/**
		 * Returns a String representation of this object, merely
		 * for debugging purposes.
		 * (Internal) Warning: Verbosity on a problematic subroutine may cause
		 * stack overflow errors due to recursive subSubs() calls.
		 * Don't use this, then.
		 */
		public String toString(){
			String ret = "Subroutine: Local variable is '"+localVariable+"', JSRs are '"+theJSRs+"', RET is '"+theRET+"', Instructions: '"+instructions.toString()+"'.";

			ret += " Accessed local variable slots: '";
			int[] alv = getAccessedLocalsIndices();
			for (int i=0; i<alv.length; i++){
				ret += alv[i]+" ";
			}
			ret+="'.";

			ret += " Recursively (via subsub...routines) accessed local variable slots: '";
			alv = getRecursivelyAccessedLocalsIndices();
			for (int i=0; i<alv.length; i++){
				ret += alv[i]+" ";
			}
			ret+="'.";

			return ret;
		}

		/**
		 * Sets the leaving RET instruction. Must be invoked after all instructions are added.
		 * Must not be invoked for top-level 'subroutine'.
		 */
		void setLeavingRET(){
			if (localVariable == UNSET){
				throw new AssertionViolatedException("setLeavingRET() called for top-level 'subroutine' or forgot to set local variable first.");
			}
			Iterator<Serializable> iter = instructions.iterator();
			InstructionHandle ret = null;
			while(iter.hasNext()){
				InstructionHandle actual = (InstructionHandle) iter.next();
				if (actual.getInstruction() instanceof RET){
					if (ret != null){
						throw new StructuralCodeConstraintException("Subroutine with more then one RET detected: '"+ret+"' and '"+actual+"'.");
					}
					else{
						ret = actual;
					}
				}
			}
			if (ret == null){
				throw new StructuralCodeConstraintException("Subroutine without a RET detected.");
			}
			if (((RET) ret.getInstruction()).getIndex() != localVariable){
				throw new StructuralCodeConstraintException("Subroutine uses '"+ret+"' which does not match the correct local variable '"+localVariable+"'.");
			}
			theRET = ret;
		}

		/*
		 * Refer to the Subroutine interface for documentation.
		 */
		public InstructionHandle[] getEnteringJsrInstructions(){
			if (this == TOPLEVEL) {
				throw new AssertionViolatedException("getLeavingRET() called on top level pseudo-subroutine.");
			}
			InstructionHandle[] jsrs = new InstructionHandle[theJSRs.size()];
			return (theJSRs.toArray(jsrs));
		}

		/**
		 * Adds a new JSR or JSR_W that has this subroutine as its target.
		 */
		public void addEnteringJsrInstruction(InstructionHandle jsrInst){
			if ( (jsrInst == null) || (! (jsrInst.getInstruction().isJsrInstruction()))){
				throw new AssertionViolatedException("Expecting JsrInstruction InstructionHandle.");
			}
			if (localVariable == UNSET){
				throw new AssertionViolatedException("Set the localVariable first!");
			}
			else{
				// Something is wrong when an ASTORE is targeted that does not operate on the same local variable than the rest of the
				// JsrInstruction-targets and the RET.
				// (We don't know out leader here so we cannot check if we're really targeted!)
				if (localVariable != ( (((InstructionBranch) jsrInst.getInstruction()).getTarget().getInstruction())).getIndex()){
					throw new AssertionViolatedException("Setting a wrong JsrInstruction.");
				}
			}
			theJSRs.add(jsrInst);
		}

		/*
		 * Refer to the Subroutine interface for documentation.
		 */
		public InstructionHandle getLeavingRET(){
			if (this == TOPLEVEL) {
				throw new AssertionViolatedException("getLeavingRET() called on top level pseudo-subroutine.");
			}
			return theRET;
		}

		/*
		 * Refer to the Subroutine interface for documentation.
		 */
		public InstructionHandle[] getInstructions(){
			InstructionHandle[] ret = new InstructionHandle[instructions.size()];
			return instructions.toArray(ret);
		}

		/*
		 * Adds an instruction to this subroutine.
		 * All instructions must have been added before invoking setLeavingRET().
		 * @see #setLeavingRET
		 */
		void addInstruction(InstructionHandle ih){
			if (theRET != null){
				throw new AssertionViolatedException("All instructions must have been added before invoking setLeavingRET().");
			}
			instructions.add(ih);
		}

		/* Satisfies Subroutine.getRecursivelyAccessedLocalsIndices(). */
		public int[] getRecursivelyAccessedLocalsIndices(){
			HashSet<Integer> s = new HashSet<Integer>();
			int[] lvs = getAccessedLocalsIndices();
			for (int j=0; j<lvs.length; j++){
				s.add(new Integer(lvs[j]));
			}
			_getRecursivelyAccessedLocalsIndicesHelper(s, this.subSubs());
			int[] ret = new int[s.size()];
			Iterator<Integer> i = s.iterator();
			int j=-1;
			while (i.hasNext()){
				j++;
				ret[j] = i.next().intValue();
			}
			return ret;
		}

		/**
		 * A recursive helper method for getRecursivelyAccessedLocalsIndices().
		 * @see #getRecursivelyAccessedLocalsIndices()
		 */
		private void _getRecursivelyAccessedLocalsIndicesHelper(HashSet<Integer> s, Subroutine[] subs){
			for (int i=0; i<subs.length; i++){
				int[] lvs = subs[i].getAccessedLocalsIndices();
				for (int j=0; j<lvs.length; j++){
					s.add(new Integer(lvs[j]));
				}
				if(subs[i].subSubs().length != 0){
					_getRecursivelyAccessedLocalsIndicesHelper(s, subs[i].subSubs());
				}
			}
		}

		/*
		 * Satisfies Subroutine.getAccessedLocalIndices().
		 */
		public int[] getAccessedLocalsIndices(){
			//TODO: Implement caching.
			HashSet<Serializable> acc = new HashSet<Serializable>();
			if (theRET == null && this != TOPLEVEL){
				throw new AssertionViolatedException("This subroutine object must be built up completely before calculating accessed locals.");
			}
			Iterator<Serializable> i = instructions.iterator();
			while (i.hasNext()){
				InstructionHandle ih = (InstructionHandle) i.next();
				// RET is not a LocalVariableInstruction in the current version of BCEL.
				if (ih.getInstruction() instanceof InstructionLV || ih.getInstruction() instanceof RET){
					int idx = ((ih.getInstruction())).getIndex();
					acc.add(new Integer(idx));
					// LONG? DOUBLE?.
					try{
						// LocalVariableInstruction instances are typed without the need to look into
						// the constant pool.
						if (ih.getInstruction() instanceof InstructionLV){
							int s = ((InstructionLV) ih.getInstruction()).getType(null).getSize();
							if (s==2) acc.add(new Integer(idx+1));
						}
					}
					catch(RuntimeException re){
						throw new AssertionViolatedException("Oops. BCEL did not like NULL as a ConstantPoolGen object.");
					}
				}
			}

			int[] ret = new int[acc.size()];
			i = acc.iterator();
			int j=-1;
			while (i.hasNext()){
				j++;
				ret[j] = ((Integer) i.next()).intValue();
			}
			return ret;
		}

		/*
		 * Satisfies Subroutine.subSubs().
		 */
		public Subroutine[] subSubs(){
			HashSet<Subroutine> h = new HashSet<Subroutine>();

			Iterator<Serializable> i = instructions.iterator();
			while (i.hasNext()){
				Instruction inst = ((InstructionHandle) i.next()).getInstruction();
				if (inst.isJsrInstruction()){
					InstructionHandle targ = ((InstructionBranch) inst).getTarget();
					h.add(getSubroutine(targ));
				}
			}
			Subroutine[] ret = new Subroutine[h.size()];
			return h.toArray(ret);
		}

		/*
		 * Sets the local variable slot the ASTORE that is targeted
		 * by the JsrInstructions of this subroutine operates on.
		 * This subroutine's RET operates on that same local variable
		 * slot, of course.
		 */
		void setLocalVariable(int i){
			if (localVariable != UNSET){
				throw new AssertionViolatedException("localVariable set twice.");
			}
			else{
				localVariable = i;
			}
		}

		/**
		 * The default constructor.
		 */
		public SubroutineImpl(){
		}

	}// end Inner Class SubrouteImpl

	/**
	 * The Hashtable containing the subroutines found.
	 * Key: InstructionHandle of the leader of the subroutine.
	 * Elements: SubroutineImpl objects.
	 */
	private Hashtable<InstructionHandle, Subroutine> subroutines = new Hashtable<InstructionHandle, Subroutine>();

	/**
	 * This is referring to a special subroutine, namely the
	 * top level. This is not really a subroutine but we use
	 * it to distinguish between top level instructions and
	 * unreachable instructions.
	 */
	public final Subroutine TOPLEVEL;

	/**
	 * Constructor.
	 * @param il A MethodGen object representing method to
	 * create the Subroutine objects of.
	 */
	public Subroutines(MethodGen mg){

		InstructionHandle[] all = mg.getInstructionList().getInstructionHandles();
		CodeExceptionGen[] handlers = mg.getExceptionHandlers();

		// Define our "Toplevel" fake subroutine.
		TOPLEVEL = new SubroutineImpl();

		// Calculate "real" subroutines.
		HashSet<InstructionHandle> sub_leaders = new HashSet<InstructionHandle>(); // Elements: InstructionHandle
		InstructionHandle ih = all[0];
		for (int i=0; i<all.length; i++){
			Instruction inst = all[i].getInstruction();
			if (inst.isJsrInstruction()){
				sub_leaders.add(((InstructionBranch) inst).getTarget());
			}
		}

		// Build up the database.
		Iterator<InstructionHandle> iter = sub_leaders.iterator();
		while (iter.hasNext()){
			SubroutineImpl sr = new SubroutineImpl();
			InstructionHandle astore = (iter.next());
			sr.setLocalVariable( ( (astore.getInstruction())).getIndex() );
			subroutines.put(astore, sr);
		}

		// Fake it a bit. We want a virtual "TopLevel" subroutine.
		subroutines.put(all[0], TOPLEVEL);
		sub_leaders.add(all[0]);

		// Tell the subroutines about their JsrInstructions.
		// Note that there cannot be a JSR targeting the top-level
		// since "Jsr 0" is disallowed in Pass 3a.
		// Instructions shared by a subroutine and the toplevel are
		// disallowed and checked below, after the BFS.
		for (int i=0; i<all.length; i++){
			Instruction inst = all[i].getInstruction();
			if (inst.isJsrInstruction()){
				InstructionHandle leader = ((InstructionBranch) inst).getTarget();
				((SubroutineImpl) getSubroutine(leader)).addEnteringJsrInstruction(all[i]);
			}
		}

		// Now do a BFS from every subroutine leader to find all the
		// instructions that belong to a subroutine.
		HashSet<InstructionHandle> instructions_assigned = new HashSet<InstructionHandle>(); // we don't want to assign an instruction to two or more Subroutine objects.

		Hashtable<InstructionHandle, Color> colors = new Hashtable<InstructionHandle, Color>(); //Graph colouring. Key: InstructionHandle, Value: java.awt.Color .

		iter = sub_leaders.iterator();
		while (iter.hasNext()){
			// Do some BFS with "actual" as the root of the graph.
			InstructionHandle actual = (iter.next());
			// Init colors
			for (int i=0; i<all.length; i++){
				colors.put(all[i], Color.white);
			}
			colors.put(actual, Color.gray);
			// Init Queue
			ArrayList<InstructionHandle> Q = new ArrayList<InstructionHandle>();
			Q.add(actual); // add(Obj) adds to the end, remove(0) removes from the start.

			/* BFS ALGORITHM MODIFICATION: Start out with multiple "root" nodes, as exception handlers are starting points of top-level code, too. [why top-level? TODO: Refer to the special JustIce notion of subroutines.]*/
			if (actual == all[0]){
				for (int j=0; j<handlers.length; j++){
					colors.put(handlers[j].getHandlerPC(), Color.gray);
					Q.add(handlers[j].getHandlerPC());
				}
			}
			/* CONTINUE NORMAL BFS ALGORITHM */

			// Loop until Queue is empty
			while (Q.size() != 0){
				InstructionHandle u = Q.remove(0);
				InstructionHandle[] successors = getSuccessors(u);
				for (int i=0; i<successors.length; i++){
					if (colors.get(successors[i]) == Color.white){
						colors.put(successors[i], Color.gray);
						Q.add(successors[i]);
					}
				}
				colors.put(u, Color.black);
			}
			// BFS ended above.
			for (int i=0; i<all.length; i++){
				if (colors.get(all[i]) == Color.black){
					((SubroutineImpl) (actual==all[0]?getTopLevel():getSubroutine(actual))).addInstruction(all[i]);
					if (instructions_assigned.contains(all[i])){
						throw new StructuralCodeConstraintException("Instruction '"+all[i]+"' is part of more than one subroutine (or of the top level and a subroutine).");
					}
					else{
						instructions_assigned.add(all[i]);
					}
				}
			}
			if (actual != all[0]){// If we don't deal with the top-level 'subroutine'
				((SubroutineImpl) getSubroutine(actual)).setLeavingRET();
			}
		}

		// Now make sure no instruction of a Subroutine is protected by exception handling code
		// as is mandated by JustIces notion of subroutines.
		for (int i=0; i<handlers.length; i++){
			InstructionHandle _protected = handlers[i].getStartPC();
			while (_protected != handlers[i].getEndPC().getNext()){// Note the inclusive/inclusive notation of "generic API" exception handlers!
				Enumeration<Subroutine> subs = subroutines.elements();
				while (subs.hasMoreElements()){
					Subroutine sub = subs.nextElement();
					if (sub != subroutines.get(all[0])){	// We don't want to forbid top-level exception handlers.
						if (sub.contains(_protected)){
							throw new StructuralCodeConstraintException("Subroutine instruction '"+_protected+"' is protected by an exception handler, '"+handlers[i]+"'. This is forbidden by the JustIce verifier due to its clear definition of subroutines.");
						}
					}
				}
				_protected = _protected.getNext();
			}
		}

		// Now make sure no subroutine is calling a subroutine
		// that uses the same local variable for the RET as themselves
		// (recursively).
		// This includes that subroutines may not call themselves
		// recursively, even not through intermediate calls to other
		// subroutines.
		noRecursiveCalls(getTopLevel(), new HashSet<Integer>());

	}

	/**
	 * This (recursive) utility method makes sure that
	 * no subroutine is calling a subroutine
	 * that uses the same local variable for the RET as themselves
	 * (recursively).
	 * This includes that subroutines may not call themselves
	 * recursively, even not through intermediate calls to other
	 * subroutines.
	 *
	 * @throws StructuralCodeConstraintException if the above constraint is not satisfied.
	 */
	private void noRecursiveCalls(Subroutine sub, HashSet<Integer> set){
		Subroutine[] subs = sub.subSubs();

		for (int i=0; i<subs.length; i++){
			int index = ((RET) (subs[i].getLeavingRET().getInstruction())).getIndex();

			if (!set.add(new Integer(index))){
				// Don't use toString() here because of possibly infinite recursive subSubs() calls then.
				SubroutineImpl si = (SubroutineImpl) subs[i];
				throw new StructuralCodeConstraintException("Subroutine with local variable '"+si.localVariable+"', JSRs '"+si.theJSRs+"', RET '"+si.theRET+"' is called by a subroutine which uses the same local variable index as itself; maybe even a recursive call? JustIce's clean definition of a subroutine forbids both.");
			}

			noRecursiveCalls(subs[i], set);

			set.remove(new Integer(index));
		}
	}

	/**
	 * Returns the Subroutine object associated with the given
	 * leader (that is, the first instruction of the subroutine).
	 * You must not use this to get the top-level instructions
	 * modeled as a Subroutine object.
	 *
	 * @see #getTopLevel()
	 */
	public Subroutine getSubroutine(InstructionHandle leader){
		Subroutine ret = subroutines.get(leader);

		if (ret == null){
			throw new AssertionViolatedException("Subroutine requested for an InstructionHandle that is not a leader of a subroutine.");
		}

		if (ret == TOPLEVEL){
			throw new AssertionViolatedException("TOPLEVEL special subroutine requested; use getTopLevel().");
		}

		return ret;
	}

	/**
	 * Returns the subroutine object associated with the
	 * given instruction. This is a costly operation, you
	 * should consider using getSubroutine(InstructionHandle).
	 * Returns 'null' if the given InstructionHandle lies
	 * in so-called 'dead code', i.e. code that can never
	 * be executed.
	 *
	 * @see #getSubroutine(InstructionHandle)
	 * @see #getTopLevel()
	 */
	public Subroutine subroutineOf(InstructionHandle any){
		Iterator<Subroutine> i = subroutines.values().iterator();
		while (i.hasNext()){
			Subroutine s = i.next();
			if (s.contains(any)) return s;
		}
System.err.println("DEBUG: Please verify '"+any+"' lies in dead code.");
		return null;
		//throw new AssertionViolatedException("No subroutine for InstructionHandle found (DEAD CODE?).");
	}

	/**
	 * For easy handling, the piece of code that is <B>not</B> a
	 * subroutine, the top-level, is also modeled as a Subroutine
	 * object.
	 * It is a special Subroutine object where <B>you must not invoke
	 * getEnteringJsrInstructions() or getLeavingRET()</B>.
	 *
	 * @see Subroutine#getEnteringJsrInstructions()
	 * @see Subroutine#getLeavingRET()
	 */
	public Subroutine getTopLevel(){
		return TOPLEVEL;
	}
	/**
	 * A utility method that calculates the successors of a given InstructionHandle
	 * <B>in the same subroutine</B>. That means, a RET does not have any successors
	 * as defined here. A JsrInstruction has its physical successor as its successor
	 * (opposed to its target) as defined here.
	 */
	private static InstructionHandle[] getSuccessors(InstructionHandle instruction){
		final InstructionHandle[] empty = new InstructionHandle[0];
		final InstructionHandle[] single = new InstructionHandle[1];
		final InstructionHandle[] pair = new InstructionHandle[2];

		Instruction inst = instruction.getInstruction();

		if (inst instanceof RET){
			return empty;
		}

		// Terminates method normally.
		if (inst.isReturnInstruction()){
			return empty;
		}

		// Terminates method abnormally, because JustIce mandates
		// subroutines not to be protected by exception handlers.
		if (inst.getOpcode()==Constants.ATHROW){
			return empty;
		}

		// See method comment.
		if (inst.isJsrInstruction()){
			single[0] = instruction.getNext();
			return single;
		}

		if (inst.getOpcode()==Constants.GOTO || inst.getOpcode()==Constants.GOTO_W){
			single[0] = ((InstructionBranch) inst).getTarget();
			return single;
		}

		if (inst instanceof InstructionBranch){
			if (inst instanceof InstructionSelect){
				// BCEL's getTargets() returns only the non-default targets,
				// thanks to Eli Tilevich for reporting.
				InstructionHandle[] matchTargets = ((InstructionSelect) inst).getTargets();
				InstructionHandle[] ret = new InstructionHandle[matchTargets.length+1];
				ret[0] = ((InstructionSelect) inst).getTarget();
				System.arraycopy(matchTargets, 0, ret, 1, matchTargets.length);
				return ret;
			}
			else{
				pair[0] = instruction.getNext();
				pair[1] = ((InstructionBranch) inst).getTarget();
				return pair;
			}
		}

		// default case: Fall through.
		single[0] = instruction.getNext();
		return single;
	}

	/**
	 * Returns a String representation of this object; merely for debugging puposes.
	 */
	public String toString(){
		return "---\n"+subroutines.toString()+"\n---\n";
	}
}
