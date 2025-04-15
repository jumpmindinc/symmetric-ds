package org.jumpmind.symmetric.ext;

import java.util.Map;

public class NodeRegistrationListener implements INodeRegistrationListener {
    @Override
    public void registrationUrlUpdated(String url) {
    }

    @Override
    public void registrationNextAttemptUpdated(int seconds) {
    }

    @Override
    public void registrationStarting(Thread thread) {
    }

    @Override
    public void registrationFailed(String message) {
    }

    @Override
    public void registrationSyncTriggers() {
    }

    @Override
    public void registrationSuccessful() {
    }

    @Override
    public Map<String, String> getRequestProperties() {
        return null;
    }
}
