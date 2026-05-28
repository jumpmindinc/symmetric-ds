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
package org.jumpmind.symmetric.staging.spi;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jumpmind.symmetric.staging.api.IStagedResource;
import org.jumpmind.symmetric.staging.api.ResourceKind;
import org.jumpmind.symmetric.staging.api.ResourceLocation;
import org.jumpmind.symmetric.staging.api.ResourceState;
import org.jumpmind.symmetric.staging.api.StagingKey;
import org.jumpmind.symmetric.staging.api.StagingOptions;

public abstract class AbstractStagingResource implements IStagedResource {
    protected final StagingKey key;
    protected final StagingOptions options;
    private final AtomicInteger references = new AtomicInteger(0);
    protected volatile ResourceState state;
    protected volatile ResourceLocation currentLocation;
    protected final Set<ResourceLocation> allLocations = EnumSet.noneOf(ResourceLocation.class);
    protected volatile long lastUpdateTime;

    protected AbstractStagingResource(StagingKey key, StagingOptions options, ResourceLocation initialLocation) {
        this.key = key;
        this.options = options;
        this.state = ResourceState.CREATE;
        this.currentLocation = initialLocation;
        this.allLocations.add(initialLocation);
        this.lastUpdateTime = System.currentTimeMillis();
    }

    @Override
    public StagingKey getKey() {
        return key;
    }

    @Override
    public String getPath() {
        return key.asPath();
    }

    @Override
    public ResourceState getState() {
        return state;
    }

    @Override
    public StagingOptions getOptions() {
        return options;
    }

    @Override
    public ResourceLocation getCurrentLocation() {
        return currentLocation;
    }

    @Override
    public synchronized Set<ResourceLocation> getAllLocations() {
        return EnumSet.copyOf(allLocations);
    }

    @Override
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    @Override
    public void refreshLastUpdateTime() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    @Override
    public boolean isBinary() {
        return options.getKind() == ResourceKind.BINARY;
    }

    @Override
    public boolean isMemoryResource() {
        return currentLocation == ResourceLocation.MEMORY;
    }

    @Override
    public boolean isFileResource() {
        File file = getFilesystemFile();
        return file != null && file.exists();
    }

    @Override
    public void reference() {
        references.incrementAndGet();
    }

    @Override
    public void dereference() {
        references.decrementAndGet();
    }

    protected int referenceCount() {
        return references.get();
    }

    protected synchronized void updateLocation(ResourceLocation newLocation, boolean retainOld) {
        if (!retainOld) {
            allLocations.clear();
        }
        allLocations.add(newLocation);
        this.currentLocation = newLocation;
        refreshLastUpdateTime();
    }

    protected synchronized void removeLocation(ResourceLocation location) {
        allLocations.remove(location);
    }
}
