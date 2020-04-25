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
import edu.utdallas.objsim.commons.process.AbstractChildProcessArguments;
import edu.utdallas.objsim.commons.relational.FieldsDom;
import org.pitest.functional.SideEffect1;
import org.pitest.util.CommunicationThread;
import org.pitest.util.ReceiveStrategy;
import org.pitest.util.SafeDataInputStream;
import org.pitest.util.SafeDataOutputStream;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Communication thread used for transferring information to/from the child process.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class ProfilerCommunicationThread extends CommunicationThread {
    private final DataReceiver receiver;

    ProfilerCommunicationThread(final ServerSocket socket,
                                final AbstractChildProcessArguments arguments) {
        this(socket, new DataSender(arguments), new DataReceiver());
    }

    ProfilerCommunicationThread(final ServerSocket socket,
                                final DataSender sender,
                                final DataReceiver receiver) {
        super(socket,sender, receiver);
        this.receiver = receiver;
    }

    final Map<String, Wrapped[]> getSnapshots() {
        return this.receiver.snapshots;
    }

    final FieldsDom getFieldsDom() {
        return this.receiver.fieldsDom;
    }

    final List<Integer> getFieldAccesses() {
        return this.receiver.fieldAccesses;
    }

    private static class DataSender implements SideEffect1<SafeDataOutputStream> {
        final AbstractChildProcessArguments arguments;

        public DataSender(final AbstractChildProcessArguments arguments) {
            this.arguments = arguments;
        }

        @Override
        public void apply(final SafeDataOutputStream dos) {
            dos.write(this.arguments);
        }
    }

    private static class DataReceiver implements ReceiveStrategy {
        final Map<String, Wrapped[]> snapshots;

        FieldsDom fieldsDom;

        List<Integer> fieldAccesses;

        public DataReceiver() {
            this.snapshots = new HashMap<>();
        }

        @Override
        @SuppressWarnings({"unchecked"})
        public void apply(final byte controlId, final SafeDataInputStream dis) {
            switch (controlId) {
                case ControlId.REPORT_SNAPSHOTS:
                    final String testName = dis.readString();
                    final Wrapped[] snapshots = dis.read(Wrapped[].class);
                    this.snapshots.put(testName, snapshots);
                    break;
                case ControlId.REPORT_FIELDS_DOM:
                    this.fieldsDom = dis.read(FieldsDom.class);
                    break;
                case ControlId.REPORT_FIELD_ACCESSES:
                    this.fieldAccesses = dis.read(ArrayList.class);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown code: " + controlId);
            }
        }
    }
}