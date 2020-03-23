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