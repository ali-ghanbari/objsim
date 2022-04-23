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

import edu.utdallas.objectutils.Wrapped;
import edu.utdallas.objsim.profiler.ControlId;
import org.pitest.util.ExitCode;
import org.pitest.util.SafeDataOutputStream;

import java.io.OutputStream;

/**
 * A set of methods used for sending information (including status codes) to/from
 * child profiler process.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class ProfilerReporter {
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
        this.dos.writeByte(ControlId.REPORT_SNAPSHOTS);
        this.dos.writeString(testName);
        this.dos.write(snapshots);
        this.dos.flush();
    }
}