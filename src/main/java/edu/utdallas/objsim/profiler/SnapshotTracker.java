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

import edu.utdallas.objectutils.Wrapped;
import edu.utdallas.objectutils.Wrapper;

import java.util.LinkedList;
import java.util.List;

public final class SnapshotTracker {
    public static final List<Wrapped> SNAPSHOTS = new LinkedList<>();

    private SnapshotTracker() {

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
            SNAPSHOTS.add(Wrapper.wrapObject(references));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
