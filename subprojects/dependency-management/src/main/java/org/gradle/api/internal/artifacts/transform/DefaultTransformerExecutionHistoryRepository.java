/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.transform;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.util.concurrent.ExecutionError;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public abstract class DefaultTransformerExecutionHistoryRepository implements TransformerExecutionHistoryRepository {
    
    private final ExecutionHistoryStore executionHistoryStore;
    private final TransformerWorkspaceProvider workspaceProvider;
    private final Cache<TransformationIdentity, ImmutableList<File>> inMemoryResultCache = CacheBuilder.newBuilder().build();

    public DefaultTransformerExecutionHistoryRepository(TransformerWorkspaceProvider workspaceProvider, ExecutionHistoryStore executionHistoryStore) {
        this.workspaceProvider = workspaceProvider;
        this.executionHistoryStore = executionHistoryStore;
    }

    @Override
    public Optional<AfterPreviousExecutionState> getPreviousExecution(String identity) {
        return Optional.ofNullable(executionHistoryStore.load(identity));
    }

    @Override
    public void persist(
        String identity,
        OriginMetadata originMetadata,
        ImplementationSnapshot implementationSnapshot,
        ImmutableSortedMap<String, ValueSnapshot> inputSnapshots,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFingerprints,
        boolean successful
    ) {
        executionHistoryStore.store(identity, originMetadata, implementationSnapshot, ImmutableList.of(), inputSnapshots, inputFileFingerprints, outputFingerprints, successful);
    }

    @Override
    public boolean hasCachedResult(TransformationIdentity identity) {
        return inMemoryResultCache.getIfPresent(identity) != null;
    }

    @Override
    public ImmutableList<File> withWorkspace(TransformationIdentity identity, TransformationWorkspaceAction workspaceAction) {
        try {
            return inMemoryResultCache.get(identity, () -> {
                    return workspaceProvider.withWorkspace(identity, workspaceAction);
                });
        } catch (ExecutionException | UncheckedException | ExecutionError e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    public void clearInMemoryCache() {
        inMemoryResultCache.invalidateAll();
    }
}
