package edu.utdallas.objsim.profiler;

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

import edu.utdallas.objsim.commons.process.AbstractChildProcessArguments;

import java.util.Collection;

/**
 * Arguments passed to the "prelude" profiler process which is responsible for
 * recording field accesses of the program.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class PreludeProfilerArguments extends AbstractChildProcessArguments {
    private static final long serialVersionUID = 1L;

    private final String whiteListPrefix;

    PreludeProfilerArguments(final String whiteListPrefix,
                             final String patchedMethodName,
                             final Collection<String> coveringTestNames) {
        super(patchedMethodName, coveringTestNames);
        this.whiteListPrefix = whiteListPrefix;
    }

    public String getWhiteListPrefix() {
        return this.whiteListPrefix;
    }
}
