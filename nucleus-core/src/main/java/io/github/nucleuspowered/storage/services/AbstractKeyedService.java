/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.storage.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
<<<<<<< HEAD
import com.google.common.collect.ImmutableSet;
=======
import com.google.common.collect.ImmutableMap;
>>>>>>> 7cb6a5b8 (Do not clear the user data cache before saving it.)
import io.github.nucleuspowered.storage.dataaccess.IDataTranslator;
import io.github.nucleuspowered.storage.dataobjects.keyed.DataKey;
import io.github.nucleuspowered.storage.dataobjects.keyed.IKeyedDataObject;
import io.github.nucleuspowered.storage.exceptions.DataLoadException;
import io.github.nucleuspowered.storage.exceptions.DataQueryException;
import io.github.nucleuspowered.storage.exceptions.DataSaveException;
import io.github.nucleuspowered.storage.persistence.IStorageRepository;
import io.github.nucleuspowered.storage.queryobjects.IQueryObject;
import io.github.nucleuspowered.storage.util.KeyedObject;
import io.vavr.Tuple2;
import io.vavr.Value;
import io.vavr.collection.Stream;
import io.vavr.control.Try;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.plugin.PluginContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractKeyedService<K, Q extends IQueryObject<K, Q>, D extends IKeyedDataObject<D>, O>
        implements IStorageService.Keyed.KeyedData<K, Q, D> {

    private final LoadingCache<K, ReentrantReadWriteLock> dataLocks =
            Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build(new CacheLoader<K, ReentrantReadWriteLock>() {
                @NonNull
                @Override
                public ReentrantReadWriteLock load(@NonNull final K key) {
                    return new ReentrantReadWriteLock();
                }
            });
<<<<<<< HEAD
    private final Cache<K, D> cache = Caffeine.newBuilder()
            .removalListener(this::onRemoval)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();
    private final Set<K> dirty = new HashSet<>();
=======
    private final Cache<UUID, D> cache = Caffeine
            .newBuilder()
            .removalListener(this::onRemoval)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();
>>>>>>> 7cb6a5b8 (Do not clear the user data cache before saving it.)

    private final Supplier<IDataTranslator<D, O>> dataTranslator;
    private final Supplier<IStorageRepository.Keyed<K, Q, O>> storageRepositorySupplier;
    private final PluginContainer pluginContainer;
    private final Consumer<D> upgrader;
    private final Consumer<D> versionSetter;

    public AbstractKeyedService(
            final Supplier<IDataTranslator<D, O>> dts,
            final Supplier<IStorageRepository.Keyed<K, Q, O>> storageRepositorySupplier,
            final Consumer<D> upgrader,
            final Consumer<D> versionSetter,
            final PluginContainer pluginContainer
    ) {
        this.pluginContainer = pluginContainer;
        this.dataTranslator = dts;
        this.upgrader = upgrader;
        this.versionSetter = versionSetter;
        this.storageRepositorySupplier = storageRepositorySupplier;
    }

    protected D createNewDataObject() {
        return this.dataTranslator.get().createNew();
    }

    protected void saveObject(final K key, final D object) throws DataSaveException {
        this.storageRepositorySupplier.get().save(key, this.dataTranslator.get().toDataAccessObject(object));
    }

    protected Map<K, D> getAllFromQuery(final Q query) throws DataQueryException, DataLoadException {
        return Stream.ofAll(this.storageRepositorySupplier.get().getAll(query).entrySet())
                .map(x -> Try.of(() -> new Tuple2<>(x.getKey(), this.dataTranslator.get().fromDataAccessObject(x.getValue()))))
                .map(Value::getOrNull)
                .reject(Objects::isNull)
                .collect(
                        Collectors.toMap(
                                x -> x._1,
                                x -> x._2
                        )
                );
    }

    protected Optional<D> getFromKey(final K key) throws DataQueryException, DataLoadException {
        final Optional<O> o = this.storageRepositorySupplier.get().get(key);
        if (o.isPresent()) {
            return Optional.of(this.dataTranslator.get().fromDataAccessObject(o.get()));
        }
        return Optional.empty();
    }

    protected Optional<KeyedObject<K, D>> getFromQuery(final Q query) throws DataQueryException, DataLoadException {
        final Optional<KeyedObject<K, O>> o = this.storageRepositorySupplier.get().get(query);
        if (o.filter(x -> x.getValue().isPresent()).isPresent()) {
            final D value = this.dataTranslator.get().fromDataAccessObject(o.get().getValue().get());
            return o.map(x -> x.mapValue(d -> value));
        }
        return Optional.empty();
    }

    public D createNew() {
        final D data = this.createNewDataObject();
        this.versionSetter.accept(data);
        return data;
    }

    @Override
    public CompletableFuture<Void> clearCache() {
        this.cache.invalidateAll();
        return ServicesUtil.run(() -> {
            this.storageRepositorySupplier.get().clearCache();
            return null;
        }, this.pluginContainer);
    }

    @Override
<<<<<<< HEAD
    public CompletableFuture<Optional<D>> get(@NonNull final K key) {
        final ReentrantReadWriteLock.ReadLock lock = this.dataLocks.get(key).readLock();
        try {
            lock.lock();
            final D result = this.cache.getIfPresent(key);
            this.dirty.add(key);
=======
    public CompletableFuture<Void> clearCacheUnless(final Set<UUID> keysToKeep) {
        final Set<UUID> keysToRemove = this.cache.asMap().keySet().stream().filter(x -> !keysToKeep.contains(x)).collect(Collectors.toSet());
        this.cache.invalidateAll(keysToRemove);
        return ServicesUtil.run(() -> {
            this.storageRepositorySupplier.get().clearCache(keysToRemove);
            return null;
        }, this.pluginContainer);
    }

    @Override
    public CompletableFuture<Optional<D>> get(@NonNull final UUID key) {
        ReentrantReadWriteLock.ReadLock lock = this.dataLocks.get(key).readLock();
        try {
            lock.lock();
            D result = this.cache.getIfPresent(key);
>>>>>>> 7cb6a5b8 (Do not clear the user data cache before saving it.)
            if (result != null) {
                return CompletableFuture.completedFuture(Optional.of(result));
            }
        } finally {
            lock.unlock();
        }

        return ServicesUtil.run(() -> this.getFromRepo(key), this.pluginContainer);
    }

    @Override
<<<<<<< HEAD
    public Optional<D> getOnThread(@NonNull final K key) {
=======
    public CompletableFuture<D> getOrNew(@Nonnull final UUID key) {
        return get(key).thenApply(d -> d.orElseGet(() -> {
            D result = createNew();
            save(key, result);
            return result;
        }));
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public Optional<D> getOnThread(@NonNull UUID key) {
>>>>>>> 7cb6a5b8 (Do not clear the user data cache before saving it.)
        // Read lock for the cache
        final ReentrantReadWriteLock.ReadLock lock = this.dataLocks.get(key).readLock();
        try {
            lock.lock();
<<<<<<< HEAD
            final D result = this.cache.getIfPresent(key);
            this.dirty.add(key);
=======
            D result = this.cache.getIfPresent(key);
>>>>>>> 7cb6a5b8 (Do not clear the user data cache before saving it.)
            if (result != null) {
                return Optional.of(result);
            }
        } finally {
            lock.unlock();
        }

        try {
            return this.getFromRepo(key);
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

<<<<<<< HEAD
    private Optional<D> getFromRepo(@NonNull final K key) throws Exception {
=======
    @SuppressWarnings("ConstantConditions")
    private Optional<D> getFromRepo(@NonNull UUID key) throws Exception {
>>>>>>> 7cb6a5b8 (Do not clear the user data cache before saving it.)
        // Write lock because of the cache
        final ReentrantReadWriteLock.WriteLock lock = this.dataLocks.get(key).writeLock();
        try {
            lock.lock();
            final Optional<D> r = this.getFromKey(key);
            r.ifPresent(d -> {
                this.upgrader.accept(d);
                this.cache.put(key, d);
            });
            return r;
        } catch (final Throwable e) {
            throw new DataLoadException("Could not get from repo", e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<Optional<KeyedObject<K, D>>> get(@NonNull final Q query) {
        return ServicesUtil.run(() -> {
            final Optional<KeyedObject<K, D>> r = this.getFromQuery(query);
            r.ifPresent(d -> {
                if (d.getValue().isPresent()) {
                    this.cache.put(d.getKey(), d.getValue().get());
                } else {
                    this.cache.invalidate(d.getKey());
                }
            });
            return r;
        }, this.pluginContainer);
    }

    @Override
    public CompletableFuture<Map<K, D>> getAll(@NonNull final Q query) {
        return ServicesUtil.run(() -> {
<<<<<<< HEAD
            final Map<K, D> res = this.getAllFromQuery(query);
            /* Map<K, D> res = r.entrySet().stream()
                    .filter(x -> x.getValue() != null)
                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, v -> dataAccess.fromDataAccessObject(v.getValue()))); */
            res.forEach((k, v) -> {
                this.cache.put(k, v);
                this.dirty.add(k);
            });
=======
            Map<UUID, D> res = this.getAll.apply(query);
            res.forEach(this.cache::put);
>>>>>>> 7cb6a5b8 (Do not clear the user data cache before saving it.)
            return res;
        }, this.pluginContainer);
    }

    @Override
    public CompletableFuture<Boolean> exists(@NonNull final K key) {
        return ServicesUtil.run(() -> this.storageRepositorySupplier.get().exists(key), this.pluginContainer);
    }

    @Override
    public CompletableFuture<Integer> count(@NonNull final Q query) {
        return ServicesUtil.run(() -> this.storageRepositorySupplier.get().count(query), this.pluginContainer);
    }

    @Override
    public <T2> CompletableFuture<Void> setAndSave(@NonNull final K key, final DataKey<T2, ? extends D> dataKey, final T2 data) {
        return this.getOrNew(key).thenAccept(x -> {
            x.set(dataKey, data);
            try {
                this.saveOnThread(key, x);
            } catch (final Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public <T2> CompletableFuture<Void> removeAndSave(@NonNull final K key, final DataKey<T2, ? extends D> dataKey) {
        return this.getOrNew(key).handle((x, ex) -> {
            x.remove(dataKey);
            try {
                this.saveOnThread(key, x);
            } catch (final Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> save(@NonNull final K key, @NonNull final D value) {
        return ServicesUtil.run(() -> {
            this.saveOnThread(key, value);
            return null;
        }, this.pluginContainer);
    }

<<<<<<< HEAD
=======
    private void saveOnThread(@NonNull final UUID key, @NonNull final D value) throws Exception {
        ReentrantReadWriteLock reentrantReadWriteLock = this.dataLocks.get(key);
        ReentrantReadWriteLock.WriteLock lock = reentrantReadWriteLock.writeLock();
        try {
            lock.lock();
            this.cache.put(key, value);
            this.save.apply(key, value);
            value.markDirty(false);
        } finally {
            lock.unlock();
        }
    }

>>>>>>> 7cb6a5b8 (Do not clear the user data cache before saving it.)
    @Override
    public CompletableFuture<Void> delete(@NonNull final K key) {
        return ServicesUtil.run(() -> {
            final ReentrantReadWriteLock reentrantReadWriteLock = this.dataLocks.get(key);
            final ReentrantReadWriteLock.WriteLock lock = reentrantReadWriteLock.writeLock();
            try {
                lock.lock();
                this.storageRepositorySupplier.get().delete(key);
                final D o = this.cache.getIfPresent(key);
                if (o != null) {
                    o.markDirty(false); // don't want to save it
                }
                this.cache.invalidate(key);
                return null;
            } finally {
                lock.unlock();
            }
        }, this.pluginContainer);
    }

    @Override
    public CompletableFuture<Void> ensureSaved() {
        return ServicesUtil.run(() -> {
<<<<<<< HEAD
            for (final K K : ImmutableSet.copyOf(this.dirty)) {
                final D d = this.cache.getIfPresent(K);
                if (d != null) {
                    this.save(K, d);
                } else {
                    this.dirty.remove(K);
=======
            for (final Map.Entry<UUID, D> objectToSave : new HashMap<>(this.cache.asMap()).entrySet()) {
                if (objectToSave.getValue() != null && objectToSave.getValue().isDirty()) {
                    this.save(objectToSave.getKey(), objectToSave.getValue());
>>>>>>> 7cb6a5b8 (Do not clear the user data cache before saving it.)
                }
            }
            return null;
        }, this.pluginContainer);
    }

    private void saveOnThread(@NonNull final K key, @NonNull final D value) throws Exception {
        final ReentrantReadWriteLock reentrantReadWriteLock = this.dataLocks.get(key);
        final ReentrantReadWriteLock.WriteLock lock = reentrantReadWriteLock.writeLock();
        try {
            lock.lock();
            this.cache.put(key, value);
            this.saveObject(key, value);
            this.dirty.remove(key);
        } finally {
            lock.unlock();
        }
    }

    private void onRemoval(@Nullable final K key, @Nullable final D dataObject, @NonNull final RemovalCause removalCause) {
        // If evicted normally, make sure it's saved.
<<<<<<< HEAD
        if (removalCause.wasEvicted() && key != null && dataObject != null) {
            this.save(key, dataObject);
            this.onEviction(key, dataObject, this.cache::put);
=======
        if (removalCause.wasEvicted() && uuid != null && dataObject != null && dataObject.isDirty()) {
            this.save(uuid, dataObject);
>>>>>>> 7cb6a5b8 (Do not clear the user data cache before saving it.)
        }
    }

    protected abstract void onEviction(final K key, final D dataObject, final BiConsumer<K, D> reAdd);
}
