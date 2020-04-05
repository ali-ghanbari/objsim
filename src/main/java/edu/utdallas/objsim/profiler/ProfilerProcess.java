/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */
package edu.utdallas.objsim.profiler;

import edu.utdallas.objectutils.Wrapped;
import org.pitest.process.ProcessArgs;
import org.pitest.process.WrappingProcess;
import org.pitest.util.ExitCode;
import org.pitest.util.SocketFinder;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

/**
 * Represents a (running) profiler process.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class ProfilerProcess {
    private final WrappingProcess process;

    private final ProfilerCommunicationThread communicationThread;

    public ProfilerProcess(final ProcessArgs processArgs,
                           final ProfilerArguments arguments) {
        this((new SocketFinder()).getNextAvailableServerSocket(), processArgs, arguments);
    }

    private ProfilerProcess(final ServerSocket socket,
                            final ProcessArgs processArgs,
                            final ProfilerArguments arguments) {
        this.process = new WrappingProcess(socket.getLocalPort(), processArgs, Profiler.class);
        this.communicationThread = new ProfilerCommunicationThread(socket, arguments);
    }

    public void start() throws IOException, InterruptedException {
        this.communicationThread.start();
        this.process.start();
    }

    public ExitCode waitToDie() {
        try {
            return this.communicationThread.waitToFinish();
        } finally {
            this.process.destroy();
        }
    }

    public Map<String, Wrapped[]> getSnapshots() {
        return this.communicationThread.getSnapshots();
    }
}