/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */

package edu.utdallas.objsim.profiler;

import edu.utdallas.objectutils.Wrapped;
import org.pitest.functional.SideEffect1;
import org.pitest.util.CommunicationThread;
import org.pitest.util.ReceiveStrategy;
import org.pitest.util.SafeDataInputStream;
import org.pitest.util.SafeDataOutputStream;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class ProfilerCommunicationThread extends CommunicationThread {
    private final DataReceiver receiver;

    public ProfilerCommunicationThread(final ServerSocket socket,
                                       final ProfilerArguments arguments) {
        this(socket, new DataSender(arguments), new DataReceiver());
    }

    public ProfilerCommunicationThread(final ServerSocket socket,
                                       final DataSender sender,
                                       final DataReceiver receiver) {
        super(socket,sender, receiver);
        this.receiver = receiver;
    }

    final Map<String, Wrapped[]> getSnapshots() {
        return this.receiver.snapshots;
    }

    private static class DataSender implements SideEffect1<SafeDataOutputStream> {
        final ProfilerArguments arguments;

        public DataSender(final ProfilerArguments arguments) {
            this.arguments = arguments;
        }

        @Override
        public void apply(final SafeDataOutputStream dos) {
            dos.write(this.arguments);
        }
    }

    private static class DataReceiver implements ReceiveStrategy {
        final Map<String, Wrapped[]> snapshots;

        public DataReceiver() {
            this.snapshots = new HashMap<>();
        }

        @Override
        public void apply(final byte __, final SafeDataInputStream dis) {
            final String testName = dis.readString();
            final Wrapped[] snapshots = dis.read(Wrapped[].class);
            this.snapshots.put(testName, snapshots);
        }
    }
}