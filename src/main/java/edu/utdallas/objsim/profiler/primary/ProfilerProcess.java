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

    ProfilerProcess(final ProcessArgs processArgs,
                    final PrimaryProfilerArguments arguments) {
        this((new SocketFinder()).getNextAvailableServerSocket(), processArgs, arguments);
    }

    private ProfilerProcess(final ServerSocket socket,
                            final ProcessArgs processArgs,
                            final PrimaryProfilerArguments arguments) {
        this.process = new WrappingProcess(socket.getLocalPort(), processArgs, PrimaryProfiler.class);
        this.communicationThread = new ProfilerCommunicationThread(socket, arguments);
    }

    void start() throws IOException, InterruptedException {
        this.communicationThread.start();
        this.process.start();
    }

    ExitCode waitToDie() {
        try {
            return this.communicationThread.waitToFinish();
        } finally {
            this.process.destroy();
        }
    }

    Map<String, Wrapped[]> getSnapshots() {
        return this.communicationThread.getSnapshots();
    }
}