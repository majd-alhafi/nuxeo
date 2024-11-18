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
 *     Salem Aouana
 */

package org.nuxeo.elasticsearch.hint;

import org.apache.lucene.search.FuzzyQuery;
import org.nuxeo.ecm.core.query.sql.model.EsHint;
import org.nuxeo.elasticsearch.api.ESHintQueryBuilder;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.services.config.ConfigurationService;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * The implementation of {@link ESHintQueryBuilder} for the <strong>"match_phrase_prefix"</strong> Elasticsearch hint
 * operator.
 *
 * @since 11.1
 */
public class MatchPhrasePrefixESHintQueryBuilder implements ESHintQueryBuilder {

    /**
     * {@inheritDoc}
     * <p>
     *
     * @return {@link org.opensearch.index.query.MatchPhrasePrefixQueryBuilder}
     */
    @Override
    public QueryBuilder make(EsHint hint, String fieldName, Object value) {
        String valueString = (String) value;
        if (valueString.endsWith("*") && valueString.length() > 2) {
            // remove useless trailing *, this is not mandatory but cleaner
            value = valueString.substring(0, valueString.length() - 1);
        }
        return QueryBuilders.matchPhrasePrefixQuery(fieldName, value)
                            .analyzer(hint.analyzer)
                            .maxExpansions(getMaxExpansions());
    }

    protected int getMaxExpansions() {
        int defaultMax = FuzzyQuery.defaultMaxExpansions;
        ConfigurationService cs = Framework.getService(ConfigurationService.class);
        if (cs != null) {
            return cs.getInteger("elasticsearch.max_expansions", defaultMax);
        }
        return defaultMax;
    }
}
