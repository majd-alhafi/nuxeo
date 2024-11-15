/*
 * (C) Copyright 2017 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Kevin Leturc
 */
package org.nuxeo.ecm.core.mongodb.seqgen;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gte;
import static com.mongodb.client.model.Filters.not;
import static org.nuxeo.runtime.mongodb.MongoDBSerializationHelper.MONGODB_ID;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.uidgen.AbstractUIDSequencer;
import org.nuxeo.ecm.core.uidgen.UIDSequencer;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.mongodb.MongoDBConnectionService;
import org.nuxeo.runtime.services.config.ConfigurationService;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;

/**
 * MongoDB implementation of {@link UIDSequencer}.
 * <p>
 * We use MongoDB upsert feature to provide a sequencer.
 *
 * @since 9.1
 */
public class MongoDBUIDSequencer extends AbstractUIDSequencer {

    private static final Logger log = LogManager.getLogger(MongoDBUIDSequencer.class);

    public static final String SEQUENCE_DATABASE_ID = "sequence";

    public static final String COLLECTION_NAME_PROPERTY = "nuxeo.mongodb.seqgen.collection.name";

    public static final String DEFAULT_COLLECTION_NAME = "sequence";

    public static final String SEQUENCE_VALUE_FIELD = "sequence";

    protected static final int ERROR_IMMUTABLE_FIELD = 66;

    protected MongoCollection<Document> coll;

    @Override
    public void init() {
        getSequencerCollection();
    }

    @Override
    public void initSequence(String key, long id) {
        validateKey(key);
        Bson filter = and(eq(MONGODB_ID, key), not(gte(SEQUENCE_VALUE_FIELD, id)));
        Document sequence = new Document();
        sequence.put(MONGODB_ID, key);
        sequence.put(SEQUENCE_VALUE_FIELD, id);
        try {
            try {
                getSequencerCollection().replaceOne(filter, sequence, new ReplaceOptions().upsert(true));
            } catch (MongoWriteException e) {
                if (e.getCode() == ERROR_IMMUTABLE_FIELD) {
                    // DocumentDB doesn't support update with an explicit _id
                    sequence.remove(MONGODB_ID);
                } else if (ErrorCategory.fromErrorCode(e.getCode()) != ErrorCategory.DUPLICATE_KEY) {
                    throw e;
                }
                // retry once, as not all server versions do server-side retries on upsert
                getSequencerCollection().replaceOne(filter, sequence, new ReplaceOptions().upsert(true));
            }
        } catch (MongoWriteException e) {
            throw new NuxeoException("Failed to update the sequence '" + key + "' with value " + id, e);
        }
    }

    public MongoCollection<Document> getSequencerCollection() {
        if (coll == null) {
            // Get collection name
            ConfigurationService configurationService = Framework.getService(ConfigurationService.class);
            String collName = configurationService.getString(COLLECTION_NAME_PROPERTY, DEFAULT_COLLECTION_NAME);
            // Get a connection to MongoDB
            MongoDBConnectionService mongoService = Framework.getService(MongoDBConnectionService.class);
            // Get database
            MongoDatabase database = mongoService.getDatabase(SEQUENCE_DATABASE_ID);
            // Get collection
            coll = database.getCollection(collName);
        }
        return coll;
    }

    @Override
    public List<String> getKeys() {
        return getSequencerCollection().find()
                                       .projection(Projections.include(MONGODB_ID))
                                       .into(new ArrayList<>())
                                       .stream()
                                       .map(document -> document.getString(MONGODB_ID))
                                       .toList();
    }

    @Override
    public long getCurrent(String key) {
        validateKey(key);
        var document = getSequencerCollection().find(eq(MONGODB_ID, key)).first();
        if (document == null) {
            return SEQUENCE_DOES_NOT_EXIST;
        }
        return document.getLong(SEQUENCE_VALUE_FIELD);
    }

    @Override
    public long getNextLong(String key) {
        return incrementBy(key, 1);
    }

    @Override
    public List<Long> getNextBlock(String key, int blockSize) {
        List<Long> ret = new ArrayList<>(blockSize);
        long last = incrementBy(key, blockSize);
        for (int i = blockSize - 1; i >= 0; i--) {
            ret.add(last - i);
        }
        return ret;
    }

    protected long incrementBy(String key, int value) {
        validateKey(key);
        FindOneAndUpdateOptions options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);
        Bson filter = eq(MONGODB_ID, key);
        Bson update = Updates.inc(SEQUENCE_VALUE_FIELD, Long.valueOf(value));
        Document sequence = getSequencerCollection().findOneAndUpdate(filter, update, options);
        // If sequence is null, we need to create it
        if (sequence == null) {
            try {
                sequence = new Document();
                sequence.put(MONGODB_ID, key);
                sequence.put(SEQUENCE_VALUE_FIELD, Long.valueOf(value));
                getSequencerCollection().insertOne(sequence);
            } catch (MongoWriteException e) {
                // There was a race condition - just re-run getNextLong
                log.trace("There was a race condition during: {} sequence insertion", key, e);
                return getNextLong(key);
            }
        }
        return sequence.getLong(SEQUENCE_VALUE_FIELD).longValue();
    }

    @Override
    public void dispose() {
        if (coll != null) {
            coll = null;
        }
    }

    protected static void validateKey(String key) {
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException("The key cannot be null or empty");
        }
    }
}
