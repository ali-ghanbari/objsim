/*
 * Copyright (C) UT Dallas - All Rights Reserved.
 * Unauthorized copying of this file via any medium is
 * strictly prohibited.
 * This code base is proprietary and confidential.
 * Written by Ali Ghanbari (ali.ghanbari@utdallas.edu).
 */
package edu.utdallas.objsim;

import edu.utdallas.objsim.commons.misc.MemberNameUtils;
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
            coveringTests[i] = MemberNameUtils.sanitizeTestName(coveringTests[i]);
        }
        return new InputRecord(patchId, suspVal, patchedMethod, classFile, coveringTests);
    }
}