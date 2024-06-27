/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.blob;

import java.util.regex.Pattern;

import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Represents computation of blob keys based on the document id suffixed by the xpath if not the main blob.
 *
 * @since 11.1
 */
public class KeyStrategyDocId implements KeyStrategy {

    // Matches "12051767-a926-425c-a7e0-dcdf02c0bc04" and "12051767-a926-425c-a7e0-dcdf02c0bc04-files_files-1-file"
    protected static final Pattern UUID_REGEX = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(-.+)?$");

    private static final KeyStrategyDocId INSTANCE = new KeyStrategyDocId();

    // in these low-level APIs we deal with unprefixed xpaths, so not file:content
    protected static final String MAIN_BLOB_XPATH = "content";

    public static KeyStrategyDocId instance() {
        return INSTANCE;
    }

    @Override
    public boolean useDeDuplication() {
        return false;
    }

    @Override
    public String getDigestFromKey(String key) {
        return null;
    }

    @Override
    public BlobWriteContext getBlobWriteContext(BlobContext blobContext) {
        String key = getKey(blobContext);
        return new BlobWriteContext(blobContext, null, () -> key, this);
    }

    protected String getKey(BlobContext blobContext) {
        String docId = blobContext.docId;
        if (docId == null) {
            throw new NuxeoException("Missing docId for key strategy");
        }
        String xpath = blobContext.xpath;
        if (MAIN_BLOB_XPATH.equals(xpath)) {
            return docId;
        }
        // avoid uncommon chars for blob key
        // e.g. files:files/1/file -> files_files-1-file
        return docId + "-" + xpath.replace(':', '_').replace('/', '-');
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof KeyStrategyDocId)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean isValidKey(String key) {
        return UUID_REGEX.matcher(key).matches();
    }

}
