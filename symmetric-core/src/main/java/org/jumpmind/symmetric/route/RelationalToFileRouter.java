package org.jumpmind.symmetric.route;

import java.io.File;
import java.util.Set;

import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.io.data.CsvData;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.data.DataEventType;
import org.jumpmind.symmetric.model.DataMetaData;
import org.jumpmind.symmetric.model.FileSnapshot;
import org.jumpmind.symmetric.model.FileSnapshot.LastEventType;
import org.jumpmind.symmetric.model.FileTriggerRouter;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.model.OutgoingBatch;
import org.jumpmind.symmetric.model.TriggerRouter;

public class RelationalToFileRouter implements IDataRouter {
    public static final String ROUTER_TYPE = "dataToFile";
    private ISymmetricEngine engine;

    public RelationalToFileRouter(ISymmetricEngine engine) {
        this.engine = engine;
    }

    @Override
    public Set<String> routeToNodes(SimpleRouterContext context, DataMetaData dataMetaData, Set<Node> nodes,
            boolean initialLoad, boolean initialLoadSelectUsed, TriggerRouter triggerRouter) {
        dataMetaData.getData().getAttribute(CsvData.ROW_DATA);
        DataContext ctx = null;
        LastEventType lastEventType = null;
        if (ctx.getData().getDataEventType() == DataEventType.INSERT) {
            lastEventType = LastEventType.CREATE;
        }
        FileTriggerRouter fileTriggerRouter = engine.getFileSyncService().getFileTriggerRouter("S3", "router", false);
        FileSnapshot snapshot = new FileSnapshot(fileTriggerRouter, null, lastEventType);
        engine.getFileSyncService().save(ctx.findTransaction(), snapshot);
        return null;
    }

    @Override
    public void completeBatch(SimpleRouterContext context, OutgoingBatch batch) {
        // TODO Auto-generated method stub
    }

    @Override
    public void contextCommitted(SimpleRouterContext context) {
        // TODO Auto-generated method stub
    }

    @Override
    public boolean isConfigurable() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isDmlOnly() {
        // TODO Auto-generated method stub
        return false;
    }
}
