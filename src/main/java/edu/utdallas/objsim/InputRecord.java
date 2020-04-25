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

import edu.utdallas.objsim.commons.misc.NameUtils;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.Validate;

import java.io.File;

/**
 * Corresponds to each row of the input CSV file
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas.edu)
 */
class InputRecord {
    final int patchId;

    final double suspVal;

    final String patchedMethod; // full name

    final File classFile;

    final String[] coveringTests;

    private InputRecord(final int patchId,
                        final double suspVal,
                        final String patchedMethod,
                        final File classFile,
                        final String[] coveringTests) {
        this.patchId = patchId;
        this.suspVal = suspVal;
        this.patchedMethod = patchedMethod;
        this.classFile = classFile;
        this.coveringTests = coveringTests;
    }

    static InputRecord fromCSVRecord(final CSVRecord record) {
        Validate.isTrue(record.size() == 5);
        final int patchId = Integer.parseInt(record.get(0));
        final double suspVal = Double.parseDouble(record.get(1));
        final String patchedMethod = record.get(2);
        final File classFile = new File(record.get(3));
        final String[] coveringTests = record.get(4).split("\\s");
        for  (int i = 0; i < coveringTests.length; i++) {
            coveringTests[i] = NameUtils.sanitizeTestName(coveringTests[i]);
        }
        return new InputRecord(patchId, suspVal, patchedMethod, classFile, coveringTests);
    }
}