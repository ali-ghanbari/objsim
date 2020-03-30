/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.profiler;

import edu.utdallas.objectutils.Wrapped;
import org.pitest.util.ExitCode;
import org.pitest.util.SafeDataOutputStream;

import java.io.OutputStream;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
public class ProfilerReporter {
    protected final SafeDataOutputStream dos;

    protected ProfilerReporter(OutputStream os) {
        this.dos = new SafeDataOutputStream(os);
    }

    public synchronized void done(final ExitCode exitCode) {
        this.dos.writeByte(ControlId.DONE);
        this.dos.writeInt(exitCode.getCode());
        this.dos.flush();
    }

    public synchronized void reportSnapshots(final String testName, final Wrapped[] snapshots) {
        this.dos.writeByte(Byte.MIN_VALUE); // ignored
        this.dos.writeString(testName);
        this.dos.write(snapshots);
        this.dos.flush();
    }
}