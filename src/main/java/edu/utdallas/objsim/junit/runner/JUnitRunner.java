/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */
package edu.utdallas.objsim.junit.runner;

import org.pitest.functional.predicate.Predicate;
import org.pitest.testapi.ResultCollector;
import org.pitest.testapi.TestUnit;

import java.util.List;

/**
 * A set of utility methods for running JUnit test cases.
 * The methods allows running entire test class or test cases selectively.
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class JUnitRunner {
    private List<TestUnit> testUnits;

    private final ResultCollector resultCollector;

    public JUnitRunner(final List<TestUnit> testUnits) {
        this.testUnits = testUnits;
        this.resultCollector = new PrinterResultCollector();
    }

    public List<TestUnit> getTestUnits() {
        return this.testUnits;
    }

    public void setTestUnits(List<TestUnit> testUnits) {
        this.testUnits = testUnits;
    }

    /**
     * Runs entire test class.
     *
     * @return <code>true</code> iff all the executed tests passed.
     */
    public boolean run() {
        return run(TestUnitFilter.all());
    }

    /**
     * Runs tests admitted by <code>shouldRun</code>.
     *
     * @param shouldRun A filter that determines whether or not a test case should be
     *                  executed.
     * @return <code>true</code> iff all the admitted test cases passed.
     */
    public boolean run(final Predicate<TestUnit> shouldRun) {
        for (final TestUnit testUnit : this.testUnits) {
            if (!shouldRun.apply(testUnit)) {
                continue;
            }
            testUnit.execute(this.resultCollector);
            if (this.resultCollector.shouldExit()) {
                System.out.println("WARNING: Running test cases is terminated.");
                return false;
            }
        }
        return true;
    }
}