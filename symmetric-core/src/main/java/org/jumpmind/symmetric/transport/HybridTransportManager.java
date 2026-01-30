package org.jumpmind.symmetric.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.jumpmind.symmetric.AbstractSymmetricEngine;
import org.jumpmind.symmetric.ISymmetricEngine;
import org.jumpmind.symmetric.model.BatchAck;
import org.jumpmind.symmetric.model.IncomingBatch;
import org.jumpmind.symmetric.model.Node;
import org.jumpmind.symmetric.service.IParameterService;
import org.jumpmind.symmetric.transport.http.HttpTransportManager;
import org.jumpmind.symmetric.transport.internal.InternalTransportManager;

public class HybridTransportManager implements ITransportManager {
    private InternalTransportManager internalTransport;
    private HttpTransportManager httpTransport;
    private IParameterService parameterService;

    public HybridTransportManager(ISymmetricEngine engine) {
        internalTransport = TransportManagerFactory.createInternalTransportManager(engine);
        httpTransport = TransportManagerFactory.createHttpTransportManager(engine);
        parameterService = engine.getParameterService();
    }

    @Override
    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local, String securityToken,
            String registrationUrl) throws IOException {
        return getTransport(remote).sendAcknowledgement(remote, list, local, securityToken, registrationUrl);
    }

    @Override
    public int sendAcknowledgement(Node remote, List<IncomingBatch> list, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return getTransport(remote).sendAcknowledgement(remote, list, local, securityToken, requestProperties, registrationUrl);
    }

    @Override
    public void writeAcknowledgement(OutputStream out, Node remote, List<IncomingBatch> list, Node local,
            String securityToken) throws IOException {
        getTransport(remote).writeAcknowledgement(out, remote, list, local, securityToken);
    }

    @Override
    public List<BatchAck> readAcknowledgement(String parameterString1, String parameterString2) throws IOException {
        return getTransport((String) null).readAcknowledgement(parameterString1, parameterString2);
    }

    @Override
    public IIncomingTransport getFilePullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return getTransport(remote).getFilePullTransport(remote, local, securityToken, requestProperties, registrationUrl);
    }

    @Override
    public IOutgoingWithResponseTransport getFilePushTransport(Node remote, Node local, String securityToken,
            String registrationUrl) throws IOException {
        return getTransport(remote).getFilePushTransport(remote, local, securityToken, registrationUrl);
    }

    @Override
    public IIncomingTransport getPullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return getTransport(remote).getPullTransport(remote, local, securityToken, requestProperties, registrationUrl);
    }

    @Override
    public IIncomingTransport getPingTransport(Node remote, Node local, String registrationUrl) throws IOException {
        return getTransport(remote).getPingTransport(remote, local, registrationUrl);
    }

    @Override
    public IOutgoingWithResponseTransport getPushTransport(Node remote, Node local, String securityToken,
            String registrationUrl) throws IOException {
        return getTransport(remote).getPushTransport(remote, local, securityToken, registrationUrl);
    }

    @Override
    public IOutgoingWithResponseTransport getPushTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return getTransport(remote).getPushTransport(remote, local, securityToken, requestProperties, registrationUrl);
    }

    @Override
    public IIncomingTransport getRegisterTransport(Node node, String registrationUrl) throws IOException {
        return getTransport(registrationUrl).getRegisterTransport(node, registrationUrl);
    }

    @Override
    public IIncomingTransport getRegisterTransport(Node node, String registrationUrl,
            Map<String, String> requestProperties) throws IOException {
        return getTransport(registrationUrl).getRegisterTransport(node, registrationUrl, requestProperties);
    }

    @Override
    public IOutgoingWithResponseTransport getRegisterPushTransport(Node remote, Node local) throws IOException {
        return getTransport(remote).getRegisterPushTransport(remote, local);
    }

    @Override
    public IIncomingTransport getConfigTransport(Node remote, Node local, String securityToken, String symmetricVersion,
            String configVersion, String registrationUrl) throws IOException {
        return getTransport(remote).getConfigTransport(remote, local, securityToken, symmetricVersion, configVersion, registrationUrl);
    }

    @Override
    public IIncomingTransport getBandwidthPullTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl, long sampleSize) throws IOException {
        return getTransport(remote).getBandwidthPullTransport(remote, local, securityToken, requestProperties, registrationUrl, sampleSize);
    }

    @Override
    public IOutgoingWithResponseTransport getBandwidthPushTransport(Node remote, Node local, String securityToken,
            Map<String, String> requestProperties, String registrationUrl) throws IOException {
        return getTransport(remote).getBandwidthPushTransport(remote, local, securityToken, requestProperties, registrationUrl);
    }

    @Override
    public IIncomingTransport getComparePullTransport(Node remote, Node local, String securityToken,
            String registrationUrl, Map<String, String> requestParameters) throws IOException {
        return getTransport(remote).getComparePullTransport(remote, local, securityToken, registrationUrl, requestParameters);
    }

    @Override
    public IOutgoingWithResponseTransport getComparePushTransport(Node remote, Node local, String securityToken,
            String registrationUrl, Map<String, String> requestParameters) throws IOException {
        return getTransport(remote).getComparePushTransport(remote, local, securityToken, registrationUrl, requestParameters);
    }

    @Override
    public String resolveURL(String url, String registrationUrl) {
        return getTransport(url).resolveURL(url, registrationUrl);
    }

    @Override
    public int sendCopyRequest(Node local) throws IOException {
        return getTransport(parameterService.getRegistrationUrl()).sendCopyRequest(local);
    }

    @Override
    public int sendStatusRequest(Node local, Map<String, String> statuses) throws IOException {
        return getTransport(parameterService.getRegistrationUrl()).sendStatusRequest(local, statuses);
    }

    @Override
    public void writeRequestProperties(Map<String, String> requestProperties, OutputStream os) throws IOException {
        getTransport((String) null).writeRequestProperties(requestProperties, os);
    }

    @Override
    public Map<String, String> readRequestProperties(InputStream is) throws IOException {
        return getTransport((String) null).readRequestProperties(is);
    }

    private ITransportManager getTransport(Node remote) {
        return getTransport(remote != null ? remote.getSyncUrl() : null);
    }

    private ITransportManager getTransport(String remoteSyncUrl) {
        return AbstractSymmetricEngine.findEngineByUrl(remoteSyncUrl) != null ? internalTransport : httpTransport;
    }
}
