/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */
package edu.utdallas.objsim.junit.runner;

import org.pitest.functional.predicate.Predicate;
import org.pitest.testapi.TestUnit;

import java.util.Collection;

import static edu.utdallas.objsim.commons.misc.MemberNameUtils.sanitizeExtendedTestName;

/**
 * A set of utility methods that produce common test unit filters.
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public final class TestUnitFilter {
    private TestUnitFilter() {

    }

    /**
     * A yes-to-all test unit filter.
     * @return A predicate that always return true
     */
    public static Predicate<TestUnit> all() {
        return new Predicate<TestUnit>() {
            @Override
            public Boolean apply(TestUnit testUnit) {
                return Boolean.TRUE;
            }
        };
    }

    /**
     * A test unit filter that admits only test suites present in <code>testUnitNames</code>.
     * @param testUnitNames A set of fully qualified names of the test cases to be admitted.
     * @return A predicates that returns true iff the input test suite name was present in
     *         <code>testUnitNames</code>
     */
    public static Predicate<TestUnit> some(final Collection<String> testUnitNames) {
        return new Predicate<TestUnit>() {
            @Override
            public Boolean apply(TestUnit testUnit) {
                final String testName = sanitizeExtendedTestName(testUnit.getDescription().getName());
                return testUnitNames.contains(testName);
            }
        };
    }
}