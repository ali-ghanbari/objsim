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

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A versatile, fast class for keeping track of accessed fields by the patched method.
 * !Internal use only!
 *
 * @author Ali Ghanbari (ali.ghanbari@utdallas)
 */
public final class FieldAccessRecorder {
    private static final int BITMAP_UNIT_SIZE = 1024;

    private static final long[] BITMAP_TEMPLATE;

    private static final Map<Integer, long[]> FIELD_ACCESS_BITMAP;

    private static final Map<Integer, Integer> METHOD_ENTRIES;

    static {
        METHOD_ENTRIES = new ConcurrentHashMap<>();
        FIELD_ACCESS_BITMAP = new ConcurrentHashMap<>();
        BITMAP_TEMPLATE = new long[BITMAP_UNIT_SIZE];
        Arrays.fill(BITMAP_TEMPLATE, 0L);
    }

    private FieldAccessRecorder() { }

    public static void inc(final int methodIndex) {
        long[] bitmap = FIELD_ACCESS_BITMAP.get(methodIndex);
        if (bitmap == null) {
            bitmap = BITMAP_TEMPLATE.clone();
            FIELD_ACCESS_BITMAP.put(methodIndex, bitmap);
        }
        Integer entries = METHOD_ENTRIES.get(methodIndex);
        if (entries == null) {
            entries = 0;
        }
        METHOD_ENTRIES.put(methodIndex, 1 + entries);
    }

    public static void dec(final int methodIndex) {
        Integer entries = METHOD_ENTRIES.get(methodIndex);
        if (entries == null) {
            throw new IllegalArgumentException();
        }
        METHOD_ENTRIES.put(methodIndex, entries - 1);
    }

    public static void registerFieldAccess(final int fieldIndex) {
        for (final Map.Entry<Integer, Integer> entry : METHOD_ENTRIES.entrySet()) {
            if (entry.getValue() > 0) {
                final long[] bitmap = FIELD_ACCESS_BITMAP.get(entry.getKey());
                final int index = (fieldIndex / Long.SIZE) % bitmap.length;
                bitmap[index] |= 1L << (fieldIndex % Long.SIZE);
            }
        }
    }

    public static HashMap<Integer, int[]> getFieldAccesses() {
        final HashMap<Integer, int[]> result = new HashMap<>();
        for (final Map.Entry<Integer, long[]> entry : FIELD_ACCESS_BITMAP.entrySet()) {
            final ArrayList<Integer> accessedFields = new ArrayList<>();
            int fieldIndex = 0;
            for (long group : entry.getValue()) {
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
            result.put(entry.getKey(), ArrayUtils.toPrimitive(accessedFields.toArray(new Integer[0])));
        }
        return result;
    }
}
