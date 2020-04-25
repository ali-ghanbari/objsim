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

import edu.utdallas.objectutils.InclusionPredicate;
import edu.utdallas.objectutils.Wrapped;
import edu.utdallas.objectutils.Wrapper;
import edu.utdallas.objsim.commons.relational.FieldsDom;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A class used for recording system state.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public final class SnapshotTracker {
    public static final List<Wrapped> SNAPSHOTS = new LinkedList<>();

    private static final Map<String, Set<String>> accessedFields;

    private final static InclusionPredicate inclusionPredicate;

    static {
        accessedFields = new HashMap<>(1024);
        inclusionPredicate = new InclusionPredicate() {
            @Override
            public boolean test(final Field field) {
                final String className = field.getDeclaringClass().getName();
                final Set<String> fieldNames = accessedFields.get(className);
                return fieldNames == null || fieldNames.contains(field.getName());
            }
        };
    }

    private SnapshotTracker() {

    }

    public static void setAccessedFields(final FieldsDom fieldsDom,
                                         final Collection<Integer> fieldIndices) throws Exception {
        accessedFields.clear();
        for (final int fieldIndex : fieldIndices) {
            final String fieldFullName = fieldsDom.get(fieldIndex);
            final int indexOfSep = fieldFullName.lastIndexOf('.');
            final String className = fieldFullName.substring(0, indexOfSep);
            final String fieldName = fieldFullName.substring(1 + indexOfSep);
            Set<String> fieldNames = accessedFields.get(className);
            if (fieldNames == null) {
                fieldNames = new HashSet<>();
                accessedFields.put(className, fieldNames);
            }
            fieldNames.add(fieldName);
        }
    }

    /**
     * Regardless of the return type of the patches method, this method should be called
     * before leaving the method.
     *
     * @param references All references accessible from the patched method, including the object
     *                   to be returned.
     */
    public static void submitSystemState(final Object[] references) {
        try {
            SNAPSHOTS.add(Wrapper.wrapObject(references, inclusionPredicate));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
