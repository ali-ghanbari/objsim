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
import edu.utdallas.objsim.commons.relational.FieldsDom;
import org.apache.commons.lang3.Validate;

import java.io.Serializable;
import java.util.Collection;

/**
 * Arguments for "primary" profiler process which is intended to record system state
 * snapshots at the exit point(s) of patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class PrimaryProfilerArguments extends AbstractChildProcessArguments {
    private static final long serialVersionUID = 1L;

    private final FieldsDom fieldsDom;

    private final Collection<Integer> accessedFields;

    PrimaryProfilerArguments(final String patchedMethodName,
                             final Collection<String> coveringTestNames,
                             final FieldsDom fieldsDom,
                             final Collection<Integer> accessedFields) {
        super(patchedMethodName, coveringTestNames);
        Validate.isInstanceOf(Serializable.class, accessedFields);
        this.fieldsDom = fieldsDom;
        this.accessedFields = accessedFields;
    }

    public FieldsDom getFieldsDom() {
        return this.fieldsDom;
    }

    public Collection<Integer> getAccessedFields() {
        return this.accessedFields;
    }
}
