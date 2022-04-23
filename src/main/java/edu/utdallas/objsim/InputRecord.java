package edu.utdallas.objsim;

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

import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Corresponds to each row of the input CSV file
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class InputRecord {
    final int patchId;

    final Set<String> patchedMethods; // full names

    final Set<File> classFiles;

    final String groundTruthLabel;

    private InputRecord(final int patchId,
                        final String[] patchedMethods,
                        final String[] classFileNames,
                        final String groundTruthLabel) {
        this.patchId = patchId;
        this.patchedMethods = new HashSet<>();
        Collections.addAll(this.patchedMethods, patchedMethods);
        final Iterator<String> pmit = this.patchedMethods.iterator();
        while (pmit.hasNext()) {
            if (pmit.next().startsWith("NOT-")) {
                pmit.remove();
            }
        }
        if (this.patchedMethods.isEmpty()) {
            throw new IllegalArgumentException();
        }
        this.classFiles = new HashSet<>();
        for (final String classFileName : classFileNames) {
            this.classFiles.add(new File(classFileName));
        }
        this.groundTruthLabel = groundTruthLabel;
    }

    static InputRecord fromCSVRecord(final CSVRecord record) {
        final int patchId = Integer.parseInt(record.get(0));
        final String[] patchedMethods = record.get(2).split(";");
        final String[] classFileNames = record.get(3).split(";");
        final String groundTruthLabel = record.get(4);
        return new InputRecord(patchId, patchedMethods, classFileNames, groundTruthLabel);
    }
}