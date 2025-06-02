/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.route;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jumpmind.db.sql.ISqlRowMapper;
import org.jumpmind.db.sql.ISqlTemplate;
import org.jumpmind.db.sql.Row;
import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.SyntaxParsingException;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A data router that uses a lookup table to map data to nodes
 */
public class LookupTableDataRouter extends AbstractDataRouter implements IDataRouter, IBuiltInExtensionPoint {
    private final static Logger log = LoggerFactory.getLogger(LookupTableDataRouter.class);
    public final static String PARAM_LOOKUP_TABLE = "LOOKUP_TABLE";
    public final static String PARAM_KEY_COLUMN = "KEY_COLUMN";
    public final static String PARAM_MAPPED_KEY_COLUMN = "LOOKUP_KEY_COLUMN";
    public final static String PARAM_EXTERNAL_ID_COLUMN = "EXTERNAL_ID_COLUMN";
    public final static String PARAM_ALL_NODES_VALUE = "ALL_NODES_VALUE";
    final static String EXPRESSION_KEY = String.format("%s.Expression.",
            LookupTableDataRouter.class.getName());
    final static String LOOKUP_TABLE_KEY = String.format("%s.Table.",
            LookupTableDataRouter.class.getName());
    private ISymmetricDialect symmetricDialect;

    public LookupTableDataRouter(ISymmetricDialect symmetricDialect) {
        this.symmetricDialect = symmetricDialect;
    }

    public LookupTableDataRouter() {
    }

    public Set<String> routeToNodes(SimpleRouterContext routingContext, DataMetaData dataMetaData,
            Set<Node> nodes, boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        Set<String> nodeIds = null;
        if (initialLoadSelectUsed && initialLoad) {
            nodeIds = toNodeIds(nodes, null);
        } else {
            Router router = dataMetaData.getRouter();
            Map<String, String> params = null;
            params = getParams(router, routingContext);
            Map<String, String> dataMap = getDataMap(dataMetaData, symmetricDialect);
            Map<String, Set<String>> lookupTable = getLookupTable(params, router, routingContext);
            String column = params.get(PARAM_KEY_COLUMN);
            if (dataMap.containsKey(column)) {
                String keyData = dataMap.get(column);
                Set<String> externalIds = lookupTable.get(keyData);
                if (externalIds != null) {
                    for (Node node : nodes) {
                        if (externalIds.contains(node.getExternalId()) || externalIds.contains(params.get(PARAM_ALL_NODES_VALUE))) {
                            nodeIds = addNodeId(node.getNodeId(), nodeIds, nodes);
                        }
                    }
                } else if (StringUtils.equals(keyData, params.get(PARAM_ALL_NODES_VALUE))) {
                    for (Node node : nodes) {
                        nodeIds = addNodeId(node.getNodeId(), nodeIds, nodes);
                    }
                }
            } else {
                log.error(
                        "Could not route data with an id of {} using the {} router because the column {} was not captured for the {} table",
                        new Object[] { dataMetaData.getData().getDataId(), getClass().getSimpleName(),
                                column, dataMetaData.getTable().getName() });
            }
        }
        return nodeIds;
    }

    /**
     * Cache parsed expressions in the context to minimize the amount of parsing we have to do when we have lots of throughput.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, String> getParams(Router router, SimpleRouterContext routingContext) {
        final String KEY = EXPRESSION_KEY + router.getRouterId();
        Map<String, String> params = (Map<String, String>) routingContext.getContextCache()
                .get(KEY);
        if (params == null) {
            params = parse(router.getRouterExpression());
            routingContext.getContextCache().put(KEY, params);
        }
        return params;
    }

    public Map<String, String> parse(String routerExpression) throws SyntaxParsingException {
        boolean valid = true;
        Map<String, String> params = new HashMap<String, String>();
        if (!StringUtils.isBlank(routerExpression)) {
            String[] expTokens = routerExpression.split("\\s");
            if (expTokens != null) {
                for (String t : expTokens) {
                    if (!StringUtils.isBlank(t)) {
                        String[] tokens = t.split("=");
                        /*
                         * tokens must be trimmed, removing leading and trailing spaces
                         */
                        if (tokens.length == 2 && !params.containsKey(tokens[0].trim())) {
                            String token1 = tokens[1].trim();
                            if (token1.equalsIgnoreCase("null")) {
                                token1 = null;
                            }
                            params.put(tokens[0].trim(), token1);
                        } else {
                            valid = false;
                            break;
                        }
                    }
                }
                if (!valid ||
                        params.size() < 4 || params.size() > 5 ||
                        !params.containsKey(PARAM_LOOKUP_TABLE) ||
                        !params.containsKey(PARAM_KEY_COLUMN) ||
                        !params.containsKey(PARAM_MAPPED_KEY_COLUMN) ||
                        !params.containsKey(PARAM_EXTERNAL_ID_COLUMN)) {
                    log.warn("The provided lookup table router expression was invalid. The full expression is " + routerExpression + ".");
                    throw new SyntaxParsingException("The provided lookup table router expression was invalid. The full expression is " + routerExpression
                            + ".");
                }
            }
        } else {
            log.warn("The provided lookup table router expression is empty");
        }
        return params;
    }

    protected class RowMapper implements ISqlRowMapper<Object> {
        private long numRows;
        private long bytes;
        private long tenSecondTimer;
        private long ts;
        private final Map<String, String> params;
        private Map<String, Set<String>> fillMap;

        public RowMapper(Map<String, Set<String>> fillMap, final Map<String, String> params) {
            this.fillMap = fillMap;
            this.params = params;
            this.numRows = 0;
            this.bytes = 0;
            this.tenSecondTimer = System.currentTimeMillis();
            this.ts = System.currentTimeMillis();
        }

        public long getNumRows() {
            return this.numRows;
        }

        public long getBytes() {
            return this.bytes;
        }

        public long getTs() {
            return this.ts;
        }

        @Override
        public Object mapRow(Row rs) {
            numRows++;
            String key = rs.getString(params.get(PARAM_MAPPED_KEY_COLUMN));
            String value = rs.getString(params.get(PARAM_EXTERNAL_ID_COLUMN));
            bytes += value.getBytes(Charset.defaultCharset()).length;
            if (System.currentTimeMillis() - tenSecondTimer > 10000) {
                log.info("Querying table {} for {} seconds, {} rows, and {} bytes", params.get(PARAM_LOOKUP_TABLE), ((System.currentTimeMillis() - ts)) / 1000,
                        numRows, bytes);
                tenSecondTimer = System.currentTimeMillis();
            }
            Set<String> ids = fillMap.get(key);
            if (ids == null) {
                ids = new HashSet<String>();
                fillMap.put(key, ids);
                bytes += key == null ? 0 : key.getBytes(Charset.defaultCharset()).length;
            }
            ids.add(value);
            return value;
        }
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Set<String>> getLookupTable(final Map<String, String> params, Router router,
            SimpleRouterContext routingContext) {
        final String CTX_CACHE_KEY = LOOKUP_TABLE_KEY + "." + params.get(PARAM_LOOKUP_TABLE);
        Map<String, Set<String>> lookupMap = (Map<String, Set<String>>) routingContext
                .getContextCache().get(CTX_CACHE_KEY);
        if (lookupMap == null) {
            ISqlTemplate template = symmetricDialect.getTargetPlatform().getSqlTemplate();
            final Map<String, Set<String>> fillMap = new HashMap<String, Set<String>>();
            RowMapper rowMapper = new RowMapper(fillMap, params);
            template.query(String.format("select %s, %s from %s",
                    params.get(PARAM_MAPPED_KEY_COLUMN), params.get(PARAM_EXTERNAL_ID_COLUMN),
                    params.get(PARAM_LOOKUP_TABLE)), rowMapper);
            if (System.currentTimeMillis() - rowMapper.getTs() > 10000) {
                log.info("Done querying table {} for {} seconds, {} rows, and {} bytes", params.get(PARAM_LOOKUP_TABLE), ((System.currentTimeMillis()
                        - rowMapper.getTs())) / 1000, rowMapper.getNumRows(), rowMapper.getBytes());
            }
            lookupMap = fillMap;
            routingContext.getContextCache().put(CTX_CACHE_KEY, lookupMap);
        }
        return lookupMap;
    }
}
