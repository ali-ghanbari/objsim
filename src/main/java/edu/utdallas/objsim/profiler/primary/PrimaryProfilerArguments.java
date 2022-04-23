package edu.utdallas.objsim.profiler.primary;

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

import edu.utdallas.objsim.commons.relational.FieldsDom;
import edu.utdallas.objsim.commons.relational.MethodsDom;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Arguments for "primary" profiler process which is intended to record system state
 * snapshots at the exit point(s) of patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class PrimaryProfilerArguments implements Serializable {
    private static final long serialVersionUID = 1L;

    private transient MethodsDom methodsDom;

    private transient FieldsDom fieldsDom;

    private transient Set<String> patchedMethods;

    final Map<Integer, int[]> accessedFieldsMap; // patched method index --> accessed fields

    final Set<String> coveringTests;

    PrimaryProfilerArguments(final Collection<String> patchedMethods,
                             final Collection<String> coveringTests,
                             final Map<Integer, int[]> accessedFieldsMap) {
        final Map<Integer, int[]> map = new HashMap<>();
        final MethodsDom methodsDom = getMethodsDom();
        for (final String methodName : patchedMethods) {
            final int methodIndex = methodsDom.indexOf(methodName);
            if (methodIndex < 0) {
                throw new IllegalArgumentException();
            }
            int[] accessedFields = accessedFieldsMap.get(methodIndex);
            if (accessedFields == null) {
                accessedFields = new int[0];
            }
            map.put(methodIndex, accessedFields);
        }
        this.accessedFieldsMap = map;
        this.coveringTests = new HashSet<>(coveringTests);
    }

    public MethodsDom getMethodsDom() {
        if (this.methodsDom == null) {
            this.methodsDom = new MethodsDom(".");
        }
        return this.methodsDom;
    }

    public FieldsDom getFieldsDom() {
        if (this.fieldsDom == null) {
            this.fieldsDom = new FieldsDom(".");
        }
        return fieldsDom;
    }

    public Set<String> getPatchedMethods() {
        if (this.patchedMethods == null) {
            final MethodsDom methodsDom = getMethodsDom();
            final Set<String> patchedMethods = new HashSet<>();
            for (final Map.Entry<Integer, int[]> entry : this.accessedFieldsMap.entrySet()) {
                patchedMethods.add(methodsDom.get(entry.getKey()));
            }
            this.patchedMethods = patchedMethods;
        }
        return this.patchedMethods;
    }
}
