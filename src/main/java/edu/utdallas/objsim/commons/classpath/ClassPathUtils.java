package edu.utdallas.objsim.commons.classpath;

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

import org.pitest.classinfo.CachingByteArraySource;
import org.pitest.classinfo.ClassByteArraySource;
import org.pitest.classpath.ClassPath;
import org.pitest.classpath.ClassPathByteArraySource;
import org.pitest.classpath.ClassloaderByteArraySource;
import org.pitest.functional.Option;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class ClassPathUtils {
    private static final int CACHE_SIZE = 200;

    public static List<File> getClassPathElements() {
        return getClassPathElements(ClassLoader.getSystemClassLoader());
    }

    public static List<File> getClassPathElements(final ClassLoader classLoader) {
        final URL[] urls = ((URLClassLoader) classLoader).getURLs();
        final List<File> elements = new ArrayList<>(urls.length);
        for(URL url: urls){
            elements.add(new File(url.getFile()));
        }
        return elements;
    }

    public static ClassByteArraySource createClassByteArraySource(final ClassPath classPath) {
        ClassByteArraySource arraySource = new ClassPathByteArraySource(classPath);
        arraySource = fallbackToClassLoader(arraySource);
        return new CachingByteArraySource(arraySource, CACHE_SIZE);
    }

    // credit: this method is adopted from PIT's source code
    private static ClassByteArraySource fallbackToClassLoader(final ClassByteArraySource arraySource) {
        final ClassByteArraySource clSource = ClassloaderByteArraySource.fromContext();
        return new ClassByteArraySource() {
            @Override
            public Option<byte[]> getBytes(String clazz) {
                final Option<byte[]> maybeBytes = arraySource.getBytes(clazz);
                if (maybeBytes.hasSome()) {
                    return maybeBytes;
                }
                return clSource.getBytes(clazz);
            }
        };
    }
}
