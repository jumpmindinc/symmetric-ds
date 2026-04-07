package org.jumpmind.symmetric.observability.models;

import java.io.Serializable;

public record ObservationLong(long value, long ms) implements ISymObservation, Serializable {
    @Override
    public long getTimestamp() {
        return ms;
    }
}
