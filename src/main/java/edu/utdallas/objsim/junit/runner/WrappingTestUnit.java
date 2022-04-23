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

import org.pitest.testapi.ResultCollector;
import org.pitest.testapi.TestUnit;

public class WrappingTestUnit implements CloseableTestUnit {
    private final TestUnit core;

    public WrappingTestUnit(final TestUnit core) {
        this.core = core;
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(ResultCollector rc) {
        this.core.execute(rc);
    }

    @Override
    public org.pitest.testapi.Description getDescription() {
        return this.core.getDescription();
    }
}
