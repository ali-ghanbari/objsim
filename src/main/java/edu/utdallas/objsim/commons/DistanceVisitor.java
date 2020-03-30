/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.commons;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public interface DistanceVisitor<T extends Number> {
    void visitDistance(final String testName, boolean wasFailing, T distance);
}
