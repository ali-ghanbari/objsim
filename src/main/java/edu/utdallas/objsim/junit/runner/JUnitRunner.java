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

import edu.utdallas.objsim.constants.Params;
import org.pitest.functional.predicate.Predicate;
import org.pitest.testapi.ResultCollector;
import org.pitest.testapi.TestUnit;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static edu.utdallas.objsim.junit.JUnitUtils.discoverTestUnits;

/**
 * A set of utility methods for running JUnit test cases.
 * The methods allows running entire test class or test cases selectively.
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class JUnitRunner {
    private static final ExecutorService EXECUTOR_SERVICE;

    static {
        EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();
    }


    private List<CloseableTestUnit> testUnits;

    private final ResultCollector resultCollector;

    private final HashSet<String> failingTests;

    public JUnitRunner(final Collection<String> testClassNames) {
        this.testUnits = discoverTestUnits(testClassNames);
        this.failingTests = new HashSet<>();
        this.resultCollector = new PrinterResultCollector(this.failingTests);
    }

    public JUnitRunner(final List<CloseableTestUnit> testUnits) {
        this.testUnits = testUnits;
        this.failingTests = new HashSet<>();
        this.resultCollector = new PrinterResultCollector(this.failingTests);
    }

    public List<CloseableTestUnit> getTestUnits() {
        return this.testUnits;
    }

    public ResultCollector getResultCollector() {
        return this.resultCollector;
    }

    public HashSet<String> getFailingTests() {
        return this.failingTests;
    }

    public void setTestUnits(List<CloseableTestUnit> testUnits) {
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
    public boolean run(final Predicate<CloseableTestUnit> shouldRun) {
        for (final CloseableTestUnit testUnit : this.testUnits) {
            if (!shouldRun.apply(testUnit)) {
                continue;
            }
            final Runnable task = new Runnable() {
                @Override
                public void run() {
                    try {
                        testUnit.execute(JUnitRunner.this.resultCollector);
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            };
            try {
                EXECUTOR_SERVICE.submit(task).get(Params.MAX_TIMEOUT_MINS, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                System.out.println("WARNING: Running the test case is terminated due to timeout.");
                testUnit.close();
                continue;
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace(System.out);
                return false;
            }
            if (this.resultCollector.shouldExit()) {
                System.out.println("WARNING: Running test cases is terminated.");
                return false;
            }
        }
        return true;
    }
}