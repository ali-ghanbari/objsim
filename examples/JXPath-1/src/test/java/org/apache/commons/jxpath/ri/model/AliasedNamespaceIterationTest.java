/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.jxpath.ri.model;

import java.util.Collection;

import org.apache.commons.jxpath.JXPathContext;
import org.apache.commons.jxpath.JXPathTestCase;
import org.apache.commons.jxpath.xml.DocumentContainer;

/**
 * Test aliased/doubled XML namespace iteration; JXPATH-125.
 *
 * @version $Revision$ $Date$
 */
public class AliasedNamespaceIterationTest extends JXPathTestCase {
    protected JXPathContext context;

    protected DocumentContainer createDocumentContainer(String model) {
        DocumentContainer result = new DocumentContainer(JXPathTestCase.class
                .getResource("IterateAliasedNS.xml"), model);
        return result;
    }

    protected JXPathContext createContext(String model) {
        JXPathContext context = JXPathContext.newContext(createDocumentContainer(model));
        context.registerNamespace("a", "ns");
        return context;
    }

    protected void doTestIterate(String xpath, String model, Collection expected) {
        assertXPathPointerIterator(createContext(model), xpath, expected);
    }

    protected void doTestIterate(String model) {
        assertXPathPointerIterator(createContext(model), "/a:doc/a:elem", list("/a:doc[1]/a:elem[1]", "/a:doc[1]/a:elem[2]"));
    }

    public void testIterateDOM() {
        doTestIterate(DocumentContainer.MODEL_DOM);
    }

    public void testIterateJDOM() {
        doTestIterate(DocumentContainer.MODEL_JDOM);
    }
}
