/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */
package edu.utdallas.objsim.junit.runner;

import org.pitest.testapi.Description;
import org.pitest.testapi.ResultCollector;

import static edu.utdallas.objsim.commons.misc.MemberNameUtils.sanitizeExtendedTestName;

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