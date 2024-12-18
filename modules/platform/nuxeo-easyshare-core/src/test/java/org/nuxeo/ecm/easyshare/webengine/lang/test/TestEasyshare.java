/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Antoine Taillefer
 */
package org.nuxeo.ecm.easyshare.webengine.lang.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.easyshare.EasyShare;
import org.nuxeo.ecm.collections.api.CollectionManager;
import org.nuxeo.ecm.collections.core.test.CollectionFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * Basic unit tests for {@link EasyShare}.
 *
 * @since 2025.0
 */
@RunWith(FeaturesRunner.class)
@Features(CollectionFeature.class)
@Deploy("nuxeo-easyshare-core:OSGI-INF/easyshare-core-contrib.xml")
public class TestEasyshare {

    @Inject
    protected CoreSession session;

    @Inject
    protected CollectionManager collectionManager;

    /**
     * Validate the following cases:
     *
     * <pre>
     * EasyShareFolder
     *   |-- File (collection member)
     *   |-- Folder (collection member)
     *   |     |-- File (child)
     *   |-- File (child)
     *   |-- Folder (child)
     *   |     |-- File (child)
     * </pre>
     */
    @Test
    public void testIsSharedDocument() {
        var easyShareFolder = session.createDocumentModel("/", "easyShareFolder", "EasyShareFolder");
        easyShareFolder = session.createDocument(easyShareFolder);

        // file member of the EasyShareFolder collection
        var sharedDoc = session.createDocumentModel("/", "sharedFile", "File");
        sharedDoc = session.createDocument(sharedDoc);
        collectionManager.addToCollection(easyShareFolder, sharedDoc, session);
        assertTrue(EasyShare.isSharedDocument(session, easyShareFolder, sharedDoc));

        // folder member of the EasyShareFolder collection
        sharedDoc = session.createDocumentModel("/", "sharedFolder", "Folder");
        sharedDoc = session.createDocument(sharedDoc);
        collectionManager.addToCollection(easyShareFolder, sharedDoc, session);
        assertTrue(EasyShare.isSharedDocument(session, easyShareFolder, sharedDoc));

        // file child of a folder member of the EasyShareFolder collection
        sharedDoc = session.createDocumentModel("/sharedFolder", "fileChild", "File");
        sharedDoc = session.createDocument(sharedDoc);
        assertTrue(EasyShare.isSharedDocument(session, easyShareFolder, sharedDoc));

        // file child of the EasyShareFolder
        sharedDoc = session.createDocumentModel("/easyShareFolder", "easyShareFileChild", "File");
        sharedDoc = session.createDocument(sharedDoc);
        assertTrue(EasyShare.isSharedDocument(session, easyShareFolder, sharedDoc));

        // folder child of the EasyShareFolder
        sharedDoc = session.createDocumentModel("/easyShareFolder", "easyShareFolderChild", "Folder");
        sharedDoc = session.createDocument(sharedDoc);
        assertTrue(EasyShare.isSharedDocument(session, easyShareFolder, sharedDoc));

        // file child of a folder child of the EasyShareFolder
        sharedDoc = session.createDocumentModel("/easyShareFolder/easyShareFolderChild", "easyShareFileGrandchild",
                "File");
        sharedDoc = session.createDocument(sharedDoc);
        assertTrue(EasyShare.isSharedDocument(session, easyShareFolder, sharedDoc));

        // file neither member of the EasyShareFolder collection nor its child
        var unsharedDoc = session.createDocumentModel("/", "unsharedFile", "File");
        unsharedDoc = session.createDocument(unsharedDoc);
        assertFalse(EasyShare.isSharedDocument(session, easyShareFolder, unsharedDoc));

        // folder neither member of the EasyShareFolder collection nor its child
        unsharedDoc = session.createDocumentModel("/", "unsharedFolder", "Folder");
        unsharedDoc = session.createDocument(unsharedDoc);
        assertFalse(EasyShare.isSharedDocument(session, easyShareFolder, unsharedDoc));
    }
}
