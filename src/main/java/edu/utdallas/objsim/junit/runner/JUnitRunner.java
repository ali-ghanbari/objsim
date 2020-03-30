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
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class JUnitRunner {
    private List<TestUnit> testUnits;

    private final ResultCollector resultCollector;

    public JUnitRunner(final List<TestUnit> testUnits) {
        this.testUnits = testUnits;
        this.resultCollector = new DefaultResultCollector();
    }

    public List<TestUnit> getTestUnits() {
        return this.testUnits;
    }

    public void setTestUnits(List<TestUnit> testUnits) {
        this.testUnits = testUnits;
    }

    public boolean run() {
        return run(TestUnitFilter.all());
    }

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