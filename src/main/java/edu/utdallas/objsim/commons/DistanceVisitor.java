/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */
package edu.utdallas.objsim.commons;

/**
 * An interface for visiting the distance of system state at the end of patched method
 * between original and patched version upon running a test case that can be failing
 * or passing.
 *
 * @param <T> The distance type which has to be a <code>Number</code>
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public interface DistanceVisitor<T extends Number> {
    /**
     * Called when a distance visited.
     *
     * @param testName Fully qualified name of the test that has executed
     * @param wasFailing Was the test identified by <code>testName</code> failing?
     * @param distance The distance of the system state at the end of patched method
     *                 between original and patched version upon running <code>testName</code>
     */
    void visitDistance(final String testName, boolean wasFailing, T distance);
}
