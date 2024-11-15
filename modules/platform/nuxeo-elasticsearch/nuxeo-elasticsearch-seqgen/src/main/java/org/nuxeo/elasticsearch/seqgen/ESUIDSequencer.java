/*
 * (C) Copyright 2015-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thierry Delprat <tdelprat@nuxeo.com>
 */
package org.nuxeo.elasticsearch.seqgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.uidgen.AbstractUIDSequencer;
import org.nuxeo.ecm.core.uidgen.UIDSequencer;
import org.nuxeo.elasticsearch.ElasticSearchConstants;
import org.nuxeo.elasticsearch.api.ESClient;
import org.nuxeo.elasticsearch.api.ElasticSearchAdmin;
import org.nuxeo.runtime.api.Framework;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.VersionType;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

/**
 * Elasticsearch implementation of {@link UIDSequencer}.
 * <p>
 * Since elasticsearch does not seem to support a notion of native sequence, the implementation uses the auto-increment
 * of the version attribute as described in the <a href=
 * "http://blogs.perl.org/users/clinton_gormley/2011/10/elasticsearchsequence---a-blazing-fast-ticket-server.html"
 * >ElasticSearch::Sequence - a blazing fast ticket server</a> blog post.
 *
 * @since 7.3
 */
public class ESUIDSequencer extends AbstractUIDSequencer {

    protected static final int MAX_RETRY = 3;

    protected ESClient esClient = null;

    protected String indexName;

    @Override
    public void init() {
        if (esClient != null) {
            return;
        }
        ElasticSearchAdmin esa = Framework.getService(ElasticSearchAdmin.class);
        esClient = esa.getClient();
        indexName = esa.getIndexNameForType(ElasticSearchConstants.SEQ_ID_TYPE);
        try {
            boolean indexExists = esClient.indexExists(indexName);
            if (!indexExists) {
                throw new NuxeoException(
                        String.format("Sequencer %s needs an elasticSearchIndex contribution with type %s", getName(),
                                ElasticSearchConstants.SEQ_ID_TYPE));
            }
        } catch (NoSuchElementException | NuxeoException e) {
            dispose();
            throw e;
        }
    }

    @Override
    public void dispose() {
        if (esClient == null) {
            return;
        }
        esClient = null;
        indexName = null;
    }

    @Override
    public void initSequence(String key, long id) {
        validateKey(key);
        String source = "{ \"ts\" : " + System.currentTimeMillis() + "}";
        esClient.index(new IndexRequest(indexName).id(key)
                                                  .versionType(VersionType.EXTERNAL)
                                                  .version(id)
                                                  .source(source, XContentType.JSON));
    }

    @Override
    public List<String> getKeys() {
        var response = esClient.search(
                new SearchRequest(indexName).source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())));
        return Stream.of(response.getHits().getHits()).map(SearchHit::getId).toList();
    }

    @Override
    public long getCurrent(String sequenceName) {
        validateKey(sequenceName);
        var document = esClient.get(new GetRequest(indexName, sequenceName));
        if (!document.isExists()) {
            return SEQUENCE_DOES_NOT_EXIST;
        }
        return document.getVersion();
    }

    @Override
    public long getNextLong(String sequenceName) {
        validateKey(sequenceName);
        String source = "{ \"ts\" : " + System.currentTimeMillis() + "}";
        IndexResponse res = esClient.index(
                new IndexRequest(indexName).id(sequenceName).source(source, XContentType.JSON));
        return res.getVersion();
    }

    @Override
    public List<Long> getNextBlock(String key, int blockSize) {
        validateKey(key);
        if (blockSize == 1) {
            return Collections.singletonList(getNextLong(key));
        }
        List<Long> ret = new ArrayList<>(blockSize);
        long first = getNextBlockWithRetry(key, blockSize);
        for (long i = 0; i < blockSize; i++) {
            ret.add(first + i);
        }
        return ret;
    }

    protected long getNextBlockWithRetry(String key, int blockSize) {
        long ret;
        for (int i = 0; i < MAX_RETRY; i++) {
            ret = getNextLong(key);
            try {
                initSequence(key, ret + blockSize - 1);
                return ret;
            } catch (ConcurrentUpdateException e) {
                if (i == MAX_RETRY - 1) {
                    throw e;
                }
            }
        }
        throw new NuxeoException("Unable to get a block of sequence");
    }

    protected static void validateKey(String key) {
        if (StringUtils.isBlank(key)) {
            throw new IllegalArgumentException("The key cannot be null or empty");
        }
    }
}
