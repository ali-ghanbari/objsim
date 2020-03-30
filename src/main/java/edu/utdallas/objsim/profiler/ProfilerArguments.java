/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.profiler;

import org.apache.commons.lang3.Validate;

import java.io.Serializable;
import java.util.Collection;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class ProfilerArguments implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String patchedMethodName;

    private final Collection<String> coveringTestNames;

    protected ProfilerArguments(final String patchedMethodName,
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