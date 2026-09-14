/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU Affero General Public License, version 3.0 (AGPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU Affero General Public License,
 * version 3.0 (AGPLv3) along with this library; if not, see
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

import java.util.Collections;
import java.util.Set;

import org.jumpmind.extension.IBuiltInExtensionPoint;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.db.ISymmetricDialect;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.Router;
import org.jumpmind.symmetric.model.TriggerRouter;
import org.jumpmind.util.SimpleClassCompiler;
import org.jumpmind.util.SimpleClassCompilerException;

/**
 * This java data router is invoked when the router_type is 'java'. The router_expression is Java code for the routeToNodes() method of a class that extends the
 * {@link AbstractDataRouter}. The class is compiled in memory and cached by both the context of the batch and the classloader. For a new batch, if the
 * router_expression is the same, the same class is used, otherwise the class is renamed, compiled, and loaded again.
 */
public class JavaDataRouter extends AbstractDataRouter implements IBuiltInExtensionPoint {
    public final static String CODE_START = "import org.jumpmind.symmetric.route.*;\n"
            + "import org.jumpmind.symmetric.model.*;\n"
            + "import org.jumpmind.symmetric.service.*;\n"
            + "import java.util.*;\npublic class JavaDataRouterExt extends JavaDataRouter { \n"
            + "   public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData, Set<Node> nodes,\n"
            + "      boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {\n\n";
    public final static String CODE_END = "\n\n   }\n}\n";
    protected ISymmetricEngine engine;
    protected ISymmetricDialect dialect;
    protected final String ROUTER_KEY = String.format("%d.JavaRouter", hashCode());

    public JavaDataRouter(ISymmetricEngine engine) {
        this.engine = engine;
        this.dialect = engine.getSymmetricDialect();
    }

    public JavaDataRouter() {
    }

    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData, Set<Node> nodes,
            boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        try {
            IDataRouter router = getCompiledClass(context, dataMetaData.getRouter());
            long ts = System.currentTimeMillis();
            Set<String> targetNodes = router.routeToNodes(context, dataMetaData, nodes, initialLoad, initialLoadSelectUsed, triggerRouter);
            context.incrementStat(System.currentTimeMillis() - ts, "javarouter.exec.ms");
            return targetNodes;
        } catch (Exception e) {
            log.error("Error in java router: " + dataMetaData.getRouter() + ".  Routing to nobody.", e);
            return Collections.emptySet();
        }
    }

    protected IDataRouter getCompiledClass(SimpleRouterContext context, Router router) throws Exception {
        if (!SimpleClassCompiler.isJdkAvailable()) {
            throw new SimpleClassCompilerException("Java router '" + router.getRouterId()
                    + "' cannot run in restricted security mode (JRE only). Configure a BSH router instead.");
        }
        IDataRouter javaRouter = (IDataRouter) context.getContextCache().get(ROUTER_KEY);
        if (javaRouter == null) {
            long ts = System.currentTimeMillis();
            String javaCode = CODE_START + router.getRouterExpression() + CODE_END;
            javaRouter = (IDataRouter) engine.getExtensionService().getCompiledClass(javaCode);
            if (javaRouter instanceof JavaDataRouter) {
                ((JavaDataRouter) javaRouter).setSymmetricEngine(engine);
            }
            context.getContextCache().put(ROUTER_KEY, javaRouter);
            context.incrementStat(System.currentTimeMillis() - ts, "javarouter.compile.ms");
        }
        return javaRouter;
    }

    @Override
    public boolean isDmlOnly() {
        return false;
    }

    public void setSymmetricEngine(ISymmetricEngine engine) {
        this.engine = engine;
        this.dialect = engine.getSymmetricDialect();
    }
}
