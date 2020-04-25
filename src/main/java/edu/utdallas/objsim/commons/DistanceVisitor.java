package edu.utdallas.objsim.commons;

/*
 * #%L
 * objsim
 * %%
 * Copyright (C) 2020 The University of Texas at Dallas
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

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
