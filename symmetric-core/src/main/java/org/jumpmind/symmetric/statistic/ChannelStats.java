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
package org.jumpmind.symmetric.statistic;

import java.util.Date;

public class ChannelStats extends AbstractNodeHostStats {
    private String channelId;
    private long dataRouted;
    private long dataUnRouted;
    private long dataExtracted;
    private long dataBytesExtracted;
    private long dataExtractedErrors;
    private long dataEventInserted;
    private long dataSent;
    private long dataBytesSent;
    private long dataSentErrors;
    private long dataLoaded;
    private long dataBytesLoaded;
    private long dataLoadedErrors;
    private long dataLoadedOutgoing;
    private long dataBytesLoadedOutgoing;
    private long dataLoadedOutgoingErrors;

    public ChannelStats() {
    }

    public ChannelStats(String nodeId, String hostName, Date startTime, Date endTime,
            String channelId) {
        super(nodeId, hostName, startTime, endTime);
        this.channelId = channelId;
    }

    public void add(ChannelStats stats) {
        dataRouted += stats.getDataRouted();
        dataUnRouted += stats.getDataUnRouted();
        dataExtracted += stats.getDataExtracted();
        dataBytesExtracted += stats.getDataBytesExtracted();
        dataExtractedErrors += stats.getDataExtractedErrors();
        dataEventInserted += stats.getDataEventInserted();
        dataSent += stats.getDataSent();
        dataBytesSent += stats.getDataBytesSent();
        dataSentErrors += stats.getDataSentErrors();
        dataLoaded += stats.getDataLoaded();
        dataBytesLoaded += stats.getDataBytesLoaded();
        dataLoadedErrors += stats.getDataLoadedErrors();
        dataLoadedOutgoing += stats.getDataLoadedOutgoing();
        dataBytesLoadedOutgoing += stats.getDataBytesLoadedOutgoing();
        dataLoadedOutgoingErrors += stats.getDataLoadedOutgoingErrors();
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public long getDataRouted() {
        return dataRouted;
    }

    public void setDataRouted(long dataRouted) {
        this.dataRouted = dataRouted;
    }

    public void incrementDataRouted(long count) {
        this.dataRouted += count;
    }

    public long getDataUnRouted() {
        return dataUnRouted;
    }

    public void setDataUnRouted(long dataUnRouted) {
        this.dataUnRouted = dataUnRouted;
    }

    public void incrementDataUnRouted(long count) {
        this.dataUnRouted += count;
    }

    public long getDataBytesExtracted() {
        return dataBytesExtracted;
    }

    public void setDataBytesExtracted(long dataExtracted) {
        this.dataBytesExtracted = dataExtracted;
    }

    public void incrementDataBytesExtracted(long count) {
        this.dataBytesExtracted += count;
    }

    public long getDataExtractedErrors() {
        return dataExtractedErrors;
    }

    public void setDataExtractedErrors(long dataExtractedErrors) {
        this.dataExtractedErrors = dataExtractedErrors;
    }

    public void incrementDataExtractedErrors(long count) {
        this.dataExtractedErrors += count;
    }

    public long getDataEventInserted() {
        return dataEventInserted;
    }

    public void setDataEventInserted(long dataEventInserted) {
        this.dataEventInserted = dataEventInserted;
    }

    public void incrementDataEventInserted(long count) {
        this.dataEventInserted += count;
    }

    public long getDataBytesSent() {
        return dataBytesSent;
    }

    public void setDataBytesSent(long dataTransmitted) {
        this.dataBytesSent = dataTransmitted;
    }

    public void incrementDataBytesSent(long count) {
        this.dataBytesSent += count;
    }

    public void setDataSentErrors(long dataTransmittedErrors) {
        this.dataSentErrors = dataTransmittedErrors;
    }

    public long getDataSentErrors() {
        return dataSentErrors;
    }

    public void incrementDataSentErrors(long count) {
        this.dataSentErrors += count;
    }

    public long getDataBytesLoaded() {
        return dataBytesLoaded;
    }

    public void setDataBytesLoaded(long dataLoaded) {
        this.dataBytesLoaded = dataLoaded;
    }

    public void incrementDataBytesLoaded(long count) {
        this.dataBytesLoaded += count;
    }

    public long getDataLoadedErrors() {
        return dataLoadedErrors;
    }

    public void setDataLoadedErrors(long dataLoadedErrors) {
        this.dataLoadedErrors = dataLoadedErrors;
    }

    public void incrementDataLoadedErrors(long count) {
        this.dataLoadedErrors += count;
    }

    public void setDataExtracted(long dataExtracted) {
        this.dataExtracted = dataExtracted;
    }

    public long getDataExtracted() {
        return dataExtracted;
    }

    public void incrementDataExtracted(long count) {
        this.dataExtracted += count;
    }

    public void setDataLoaded(long dataLoaded) {
        this.dataLoaded = dataLoaded;
    }

    public long getDataLoaded() {
        return dataLoaded;
    }

    public void incrementDataLoaded(long count) {
        this.dataLoaded += count;
    }

    public void setDataSent(long dataTransmitted) {
        this.dataSent = dataTransmitted;
    }

    public long getDataSent() {
        return dataSent;
    }

    public void incrementDataSent(long count) {
        this.dataSent += count;
    }

    public long getDataLoadedOutgoing() {
        return dataLoadedOutgoing;
    }

    public void setDataLoadedOutgoing(long dataLoadedOutgoing) {
        this.dataLoadedOutgoing = dataLoadedOutgoing;
    }

    public void incrementDataLoadedOutgoing(long dataLoadedOutgoing) {
        this.dataLoadedOutgoing += dataLoadedOutgoing;
    }

    public long getDataBytesLoadedOutgoing() {
        return dataBytesLoadedOutgoing;
    }

    public void setDataBytesLoadedOutgoing(long dataBytesLoadedOutgoing) {
        this.dataBytesLoadedOutgoing = dataBytesLoadedOutgoing;
    }

    public void incrementDataBytesLoadedOutgoing(long dataBytesLoadedOutgoing) {
        this.dataBytesLoadedOutgoing += dataBytesLoadedOutgoing;
    }

    public long getDataLoadedOutgoingErrors() {
        return dataLoadedOutgoingErrors;
    }

    public void setDataLoadedOutgoingErrors(long dataLoadedOutgoingErrors) {
        this.dataLoadedOutgoingErrors = dataLoadedOutgoingErrors;
    }

    public void incrementDataLoadedOutgoingErrors(long dataLoadedOutgoingErrors) {
        this.dataLoadedOutgoingErrors += dataLoadedOutgoingErrors;
    }
}