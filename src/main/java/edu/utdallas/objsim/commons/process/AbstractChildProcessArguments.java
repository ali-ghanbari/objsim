package edu.utdallas.objsim.commons.process;

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

import org.apache.commons.lang3.Validate;

import java.io.Serializable;
import java.util.Collection;

/**
 * A bundle of information that is transferred to the child process once it is created.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public abstract class AbstractChildProcessArguments implements Serializable {
    private static final long serialVersionUID = 1L;

    protected final String patchedMethodName;

    protected final Collection<String> coveringTestNames;

    protected AbstractChildProcessArguments(final String patchedMethodName,
                                            final Collection<String> coveringTestNames) {
        Validate.isInstanceOf(Serializable.class, coveringTestNames);
        this.patchedMethodName = patchedMethodName;
        this.coveringTestNames = coveringTestNames;
    }

    public String getPatchedMethodName() {
        return this.patchedMethodName;
    }

    public Collection<String> getCoveringTestNames() {
        return this.coveringTestNames;
    }
}