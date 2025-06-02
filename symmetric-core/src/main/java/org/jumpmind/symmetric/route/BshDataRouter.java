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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.TriggerRouter;

import bsh.EvalError;
import bsh.Interpreter;
import bsh.TargetError;

/**
 * This data router is invoked when the router_type is 'bsh'. The router_expression is always a bean shell expression. See
 * <a href='http://www.beanshell.org'>the bean shell site</a> for information about the capabilities of the bean shell scripting language.
 * <P/>
 * Bound to the interpreter are the names of both the current and old column values. They can be used in the expression. They should always be referenced using
 * upper case. Also bound to the interpreter is a {@link Collection} of targetNodes. The script is expected to add the the list of target nodes a list of the
 * node_ids that should be routed to.
 */
public class BshDataRouter extends AbstractDataRouter implements IBuiltInExtensionPoint {
    protected ISymmetricEngine engine;
    final String INTERPRETER_KEY = String.format("%d.BshInterpreter", hashCode());

    public BshDataRouter(ISymmetricEngine engine) {
        this.engine = engine;
    }

    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData,
            Set<Node> nodes, boolean initialLoad, boolean initialLoadSelectUsed,
            TriggerRouter triggerRouter) {
        Set<String> boundVariableNames = new LinkedHashSet<String>();
        try {
            long ts = System.currentTimeMillis();
            Interpreter interpreter = getInterpreter(context);
            context.incrementStat(System.currentTimeMillis() - ts, "bsh.init.ms");
            HashSet<String> targetNodes = new HashSet<String>();
            ts = System.currentTimeMillis();
            bind(interpreter, dataMetaData, nodes, targetNodes, boundVariableNames, initialLoad);
            context.incrementStat(System.currentTimeMillis() - ts, "bsh.bind.ms");
            ts = System.currentTimeMillis();
            Object returnValue = interpreter.eval(dataMetaData.getRouter().getRouterExpression());
            context.incrementStat(System.currentTimeMillis() - ts, "bsh.eval.ms");
            return eval(returnValue, nodes, targetNodes);
        } catch (EvalError e) {
            if (e instanceof TargetError) {
                Throwable t = ((TargetError) e).getTarget();
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else {
                    throw new RuntimeException("Routing script failed at line " + ((TargetError) e).getErrorLineNumber(), t);
                }
            } else {
                throw new RuntimeException("Failed to evaluate bsh router script.  Bound variables were: " + boundVariableNames, e);
            }
        }
    }

    protected Interpreter getInterpreter(SimpleRouterContext context) {
        Interpreter interpreter = (Interpreter) context.getContextCache().get(INTERPRETER_KEY);
        if (interpreter == null) {
            interpreter = new Interpreter();
            context.getContextCache().put(INTERPRETER_KEY, interpreter);
        }
        return interpreter;
    }

    protected Set<String> eval(Object value, Set<Node> nodes, Set<String> targetNodes) {
        targetNodes.remove(null);
        if (targetNodes.size() > 0) {
            return targetNodes;
        } else if (value instanceof Set<?>) {
            Set<?> values = (Set<?>) value;
            Set<String> nodeIds = new HashSet<String>(values.size());
            for (Object v : values) {
                if (v != null) {
                    nodeIds.add(v.toString());
                }
            }
            return nodeIds;
        } else if (value instanceof Boolean && value.equals(Boolean.TRUE)) {
            return toNodeIds(nodes, null);
        } else if (value instanceof String) {
            Set<String> node = new HashSet<String>(1);
            node.add(value.toString());
            return node;
        } else {
            return Collections.emptySet();
        }
    }

    protected void bind(Interpreter interpreter, DataMetaData dataMetaData, Set<Node> nodes,
            Set<String> targetNodes, Set<String> boundVariableNames, boolean initialLoad) throws EvalError {
        bind(interpreter, boundVariableNames, "log", log);
        bind(interpreter, boundVariableNames, "initialLoad", initialLoad);
        bind(interpreter, boundVariableNames, "dataMetaData", dataMetaData);
        bind(interpreter, boundVariableNames, "nodes", nodes);
        bind(interpreter, boundVariableNames, "nodeIds", toNodeIds(nodes, null));
        bind(interpreter, boundVariableNames, "identityNodeId", engine.getNodeService().findIdentityNodeId());
        bind(interpreter, boundVariableNames, "targetNodes", targetNodes);
        bind(interpreter, boundVariableNames, "engine", engine);
        Map<String, Object> params = getDataObjectMap(dataMetaData, engine.getSymmetricDialect(),
                true);
        if (params != null) {
            for (String param : params.keySet()) {
                bind(interpreter, boundVariableNames, param, params.get(param));
            }
        }
    }

    protected void bind(Interpreter interpreter, Set<String> boundVariableNames, String name, Object value) throws EvalError {
        interpreter.set(name, value);
        boundVariableNames.add(name);
    }

    @Override
    public boolean isDmlOnly() {
        return false;
    }
}
