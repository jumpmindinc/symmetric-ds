package org.jumpmind.symmetric.observability.models;

import java.io.Serializable;

import org.jumpmind.symmetric.observability.interfaces.ISymObservation;

public record ObservationDouble(double value, long ms) implements ISymObservation, Serializable {
    @Override
    public double getValueAsDouble() {
        return this.value;
    }

    @Override
    public long getTimestamp() {
        return this.ms;
    }
}
