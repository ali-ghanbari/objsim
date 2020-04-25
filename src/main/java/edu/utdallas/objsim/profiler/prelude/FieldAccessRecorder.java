package edu.utdallas.objsim.profiler.prelude;

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

import java.util.ArrayList;

/**
 * A versatile, fast class for keeping track of accessed fields by the patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas)
 */
public final class FieldAccessRecorder {
    private static final int BITMAP_UNIT_SIZE = 1024;

    private static final long[][] fieldAccessBitmap;

    private static int methodEntries;

    static {
        methodEntries = 0;
        fieldAccessBitmap = new long[2][];
        fieldAccessBitmap[0] = new long[1]; // dummy
        fieldAccessBitmap[1] = new long[BITMAP_UNIT_SIZE];
    }

    private FieldAccessRecorder() {

    }

    public static void inc() {
        methodEntries++;
    }

    public static void dec() {
        methodEntries--;
    }

    public static void registerFieldAccess(final int fieldIndex) {
        final long[] bitmap = fieldAccessBitmap[methodEntries > 0 ? 1 : 0];
        final int index = (fieldIndex / Long.SIZE) % bitmap.length;
        bitmap[index] |= 1L << (fieldIndex % Long.SIZE);
    }

    public static ArrayList<Integer> getFieldAccesses() {
        final ArrayList<Integer> accessedFields = new ArrayList<>();
        int fieldIndex = 0;
        for (long group : fieldAccessBitmap[1]) {
            if (group == 0) {
                fieldIndex += Long.SIZE;
                continue;
            }
            while (group != 0) {
                if ((group & 1L) != 0) {
                    accessedFields.add(fieldIndex);
                }
                group >>>= 1;
                fieldIndex++;
            }
        }
        accessedFields.trimToSize();
        return accessedFields;
    }
}
