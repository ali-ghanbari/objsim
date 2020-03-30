/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.profiler;

import edu.utdallas.objectutils.Wrapped;
import edu.utdallas.objectutils.Wrapper;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
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
