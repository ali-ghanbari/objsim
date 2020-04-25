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

import org.pitest.functional.predicate.Predicate;
import org.pitest.testapi.TestUnit;

import java.util.Collection;

import static edu.utdallas.objsim.commons.misc.NameUtils.sanitizeExtendedTestName;

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