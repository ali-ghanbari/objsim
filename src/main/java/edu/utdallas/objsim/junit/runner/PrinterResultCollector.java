package edu.utdallas.objsim.junit.runner;

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

import org.pitest.testapi.Description;
import org.pitest.testapi.ResultCollector;

import static edu.utdallas.objsim.commons.misc.NameUtils.sanitizeExtendedTestName;

/**
 * A result collector that prints out the name of the test cases as they are executed.
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class PrinterResultCollector implements ResultCollector {
    public PrinterResultCollector() {
    }

    @Override
    public void notifyEnd(Description description, Throwable t) {
        if (t != null) {
            System.out.flush();
            System.err.println();
            t.printStackTrace();
            System.err.println();
            System.err.flush();
        }
    }

    @Override
    public void notifyEnd(Description description) {
        // nothing
    }

    @Override
    public void notifyStart(Description description) {
        final String testName = sanitizeExtendedTestName(description.getName());
        System.out.println("RUNNING: " + testName + "... ");
    }

    @Override
    public void notifySkipped(Description description) {
        final String testName = sanitizeExtendedTestName(description.getName());
        System.out.println("SKIPPED: " + testName);
    }

    @Override
    public boolean shouldExit() {
        return false;
    }
}