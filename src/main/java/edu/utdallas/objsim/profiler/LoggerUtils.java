/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.profiler;

import org.pitest.functional.SideEffect1;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public final class LoggerUtils {
    private static final Object LOCK = new Object();

    private LoggerUtils() {

    }

    public static SideEffect1<String> out() {
        return new SideEffect1<String>() {
            @Override
            public void apply(final String msg) {
                synchronized (LOCK) {
                    System.out.print(msg);
                    System.out.flush();
                }
            }
        };
    }

    public static SideEffect1<String> err() {
        return new SideEffect1<String>() {
            @Override
            public void apply(final String msg) {
                synchronized (LOCK) {
                    System.out.print(msg);
                    System.out.flush();
                }
            }
        };
    }
}