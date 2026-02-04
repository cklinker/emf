package com.emf.runtime.registry;

import com.emf.runtime.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConcurrentCollectionRegistry}.
 * 
 * Validates: Requirements 2.1 - Thread-safe storage of collection definitions
 * Validates: Requirements 2.2 - Atomic updates using copy-on-write semantics
 * Validates: Requirements 2.3 - Version tracking for each collection definition
 * Validates: Requirements 2.4 - Listener notification on collection changes
 * Validates: Requirements 2.5 - Concurrent read operations without blocking
 * Validates: Requirements 15.1 - Concurrent read operations without locking
 * Validates: Requirements 15.2 - Serialized write operations to prevent race conditions
 */
@DisplayName("ConcurrentCollectionRegistry Tests")
class ConcurrentCollectionRegistryTest {

    private static final Instant NOW = Instant.now();
    
    private ConcurrentCollectionRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new ConcurrentCollectionRegistry();
    }
    
    private CollectionDefinition createCollection(String name) {
        return new CollectionDefinition(
            name,
            name.substring(0, 1).toUpperCase() + name.substring(1),
            "Description for " + name,
            List.of(FieldDefinition.requiredString("name"), FieldDefinition.doubleField("price")),
            StorageConfig.physicalTable("tbl_" + name),
            ApiConfig.allEnabled("/api/collections/" + name),
            AuthzConfig.disabled(),
            EventsConfig.disabled(),
            1L,
            NOW,
            NOW
        );
    }
    
    private CollectionDefinition createCollectionWithVersion(String name, long version) {
        return new CollectionDefinition(
            name,
            name.substring(0, 1).toUpperCase() + name.substring(1),
            "Description for " + name,
            List.of(FieldDefinition.requiredString("name")),
            StorageConfig.physicalTable("tbl_" + name),
            ApiConfig.allEnabled("/api/collections/" + name),
            AuthzConfig.disabled(),
            EventsConfig.disabled(),
            version,
            NOW,
            NOW
        );
    }

    @Nested
    @DisplayName("Register Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should register a new collection")
        void shouldRegisterNewCollection() {
            CollectionDefinition collection = createCollection("products");
            
            registry.register(collection);
            
            CollectionDefinition retrieved = registry.get("products");
            assertNotNull(retrieved);
            assertEquals("products", retrieved.name());
        }

        @Test
        @DisplayName("Should throw NullPointerException when registering null")
        void shouldThrowWhenRegisteringNull() {
            assertThrows(NullPointerException.class, () -> registry.register(null));
        }

        @Test
        @DisplayName("Should update existing collection")
        void shouldUpdateExistingCollection() {
            CollectionDefinition original = createCollection("products");
            registry.register(original);
            
            CollectionDefinition updated = new CollectionDefinition(
                "products",
                "Updated Products",
                "Updated description",
                List.of(FieldDefinition.requiredString("name"), FieldDefinition.integer("quantity")),
                StorageConfig.physicalTable("tbl_products"),
                ApiConfig.allEnabled("/api/collections/products"),
                AuthzConfig.disabled(),
                EventsConfig.disabled(),
                1L,
                NOW,
                NOW
            );
            
            registry.register(updated);
            
            CollectionDefinition retrieved = registry.get("products");
            assertEquals("Updated Products", retrieved.displayName());
            assertEquals("Updated description", retrieved.description());
        }

        @Test
        @DisplayName("Should increment version on update when version not already incremented")
        void shouldIncrementVersionOnUpdate() {
            CollectionDefinition original = createCollectionWithVersion("products", 1L);
            registry.register(original);
            
            CollectionDefinition updated = createCollectionWithVersion("products", 1L);
            registry.register(updated);
            
            CollectionDefinition retrieved = registry.get("products");
            assertEquals(2L, retrieved.version());
        }

        @Test
        @DisplayName("Should preserve version when already incremented")
        void shouldPreserveVersionWhenAlreadyIncremented() {
            CollectionDefinition original = createCollectionWithVersion("products", 1L);
            registry.register(original);
            
            CollectionDefinition updated = createCollectionWithVersion("products", 5L);
            registry.register(updated);
            
            CollectionDefinition retrieved = registry.get("products");
            assertEquals(5L, retrieved.version());
        }

        @Test
        @DisplayName("Should increment registry version on register")
        void shouldIncrementRegistryVersionOnRegister() {
            long initialVersion = registry.getRegistryVersion();
            
            registry.register(createCollection("products"));
            
            assertEquals(initialVersion + 1, registry.getRegistryVersion());
        }
    }

    @Nested
    @DisplayName("Get Tests")
    class GetTests {

        @Test
        @DisplayName("Should return null for non-existent collection")
        void shouldReturnNullForNonExistentCollection() {
            assertNull(registry.get("nonexistent"));
        }

        @Test
        @DisplayName("Should return registered collection")
        void shouldReturnRegisteredCollection() {
            CollectionDefinition collection = createCollection("products");
            registry.register(collection);
            
            CollectionDefinition retrieved = registry.get("products");
            
            assertNotNull(retrieved);
            assertEquals("products", retrieved.name());
        }

        @Test
        @DisplayName("Should return null for null collection name")
        void shouldReturnNullForNullCollectionName() {
            assertNull(registry.get(null));
        }
    }

    @Nested
    @DisplayName("GetAllCollectionNames Tests")
    class GetAllCollectionNamesTests {

        @Test
        @DisplayName("Should return empty set when no collections registered")
        void shouldReturnEmptySetWhenNoCollections() {
            Set<String> names = registry.getAllCollectionNames();
            
            assertTrue(names.isEmpty());
        }

        @Test
        @DisplayName("Should return all registered collection names")
        void shouldReturnAllRegisteredNames() {
            registry.register(createCollection("products"));
            registry.register(createCollection("orders"));
            registry.register(createCollection("customers"));
            
            Set<String> names = registry.getAllCollectionNames();
            
            assertEquals(3, names.size());
            assertTrue(names.contains("products"));
            assertTrue(names.contains("orders"));
            assertTrue(names.contains("customers"));
        }

        @Test
        @DisplayName("Should return immutable set")
        void shouldReturnImmutableSet() {
            registry.register(createCollection("products"));
            
            Set<String> names = registry.getAllCollectionNames();
            
            assertThrows(UnsupportedOperationException.class, () -> names.add("test"));
        }
    }

    @Nested
    @DisplayName("Unregister Tests")
    class UnregisterTests {

        @Test
        @DisplayName("Should unregister existing collection")
        void shouldUnregisterExistingCollection() {
            registry.register(createCollection("products"));
            
            registry.unregister("products");
            
            assertNull(registry.get("products"));
            assertFalse(registry.contains("products"));
        }

        @Test
        @DisplayName("Should do nothing when unregistering non-existent collection")
        void shouldDoNothingWhenUnregisteringNonExistent() {
            long versionBefore = registry.getRegistryVersion();
            
            registry.unregister("nonexistent");
            
            assertEquals(versionBefore, registry.getRegistryVersion());
        }

        @Test
        @DisplayName("Should do nothing when unregistering null")
        void shouldDoNothingWhenUnregisteringNull() {
            registry.register(createCollection("products"));
            
            registry.unregister(null);
            
            assertEquals(1, registry.size());
        }

        @Test
        @DisplayName("Should increment registry version on unregister")
        void shouldIncrementRegistryVersionOnUnregister() {
            registry.register(createCollection("products"));
            long versionAfterRegister = registry.getRegistryVersion();
            
            registry.unregister("products");
            
            assertEquals(versionAfterRegister + 1, registry.getRegistryVersion());
        }
    }

    @Nested
    @DisplayName("Listener Tests")
    class ListenerTests {

        @Test
        @DisplayName("Should notify listener on new registration")
        void shouldNotifyListenerOnNewRegistration() {
            List<CollectionDefinition> registered = new ArrayList<>();
            registry.addListener(new CollectionChangeListener() {
                @Override
                public void onCollectionRegistered(CollectionDefinition definition) {
                    registered.add(definition);
                }
                @Override
                public void onCollectionUpdated(CollectionDefinition oldDef, CollectionDefinition newDef) {}
                @Override
                public void onCollectionUnregistered(String name) {}
            });
            
            registry.register(createCollection("products"));
            
            assertEquals(1, registered.size());
            assertEquals("products", registered.get(0).name());
        }

        @Test
        @DisplayName("Should notify listener on update")
        void shouldNotifyListenerOnUpdate() {
            List<CollectionDefinition> oldDefs = new ArrayList<>();
            List<CollectionDefinition> newDefs = new ArrayList<>();
            registry.addListener(new CollectionChangeListener() {
                @Override
                public void onCollectionRegistered(CollectionDefinition definition) {}
                @Override
                public void onCollectionUpdated(CollectionDefinition oldDef, CollectionDefinition newDef) {
                    oldDefs.add(oldDef);
                    newDefs.add(newDef);
                }
                @Override
                public void onCollectionUnregistered(String name) {}
            });
            
            registry.register(createCollection("products"));
            registry.register(createCollectionWithVersion("products", 5L));
            
            assertEquals(1, oldDefs.size());
            assertEquals(1, newDefs.size());
            assertEquals(1L, oldDefs.get(0).version());
            assertEquals(5L, newDefs.get(0).version());
        }

        @Test
        @DisplayName("Should notify listener on unregister")
        void shouldNotifyListenerOnUnregister() {
            List<String> unregistered = new ArrayList<>();
            registry.addListener(new CollectionChangeListener() {
                @Override
                public void onCollectionRegistered(CollectionDefinition definition) {}
                @Override
                public void onCollectionUpdated(CollectionDefinition oldDef, CollectionDefinition newDef) {}
                @Override
                public void onCollectionUnregistered(String name) {
                    unregistered.add(name);
                }
            });
            
            registry.register(createCollection("products"));
            registry.unregister("products");
            
            assertEquals(1, unregistered.size());
            assertEquals("products", unregistered.get(0));
        }

        @Test
        @DisplayName("Should not notify listener after removal")
        void shouldNotNotifyListenerAfterRemoval() {
            AtomicInteger callCount = new AtomicInteger(0);
            CollectionChangeListener listener = new CollectionChangeListener() {
                @Override
                public void onCollectionRegistered(CollectionDefinition definition) {
                    callCount.incrementAndGet();
                }
                @Override
                public void onCollectionUpdated(CollectionDefinition oldDef, CollectionDefinition newDef) {}
                @Override
                public void onCollectionUnregistered(String name) {}
            };
            
            registry.addListener(listener);
            registry.register(createCollection("products"));
            assertEquals(1, callCount.get());
            
            registry.removeListener(listener);
            registry.register(createCollection("orders"));
            assertEquals(1, callCount.get()); // Should not have been called again
        }

        @Test
        @DisplayName("Should throw NullPointerException when adding null listener")
        void shouldThrowWhenAddingNullListener() {
            assertThrows(NullPointerException.class, () -> registry.addListener(null));
        }

        @Test
        @DisplayName("Should handle listener exceptions gracefully")
        void shouldHandleListenerExceptionsGracefully() {
            AtomicInteger successCount = new AtomicInteger(0);
            
            // Add a listener that throws
            registry.addListener(new CollectionChangeListener() {
                @Override
                public void onCollectionRegistered(CollectionDefinition definition) {
                    throw new RuntimeException("Test exception");
                }
                @Override
                public void onCollectionUpdated(CollectionDefinition oldDef, CollectionDefinition newDef) {}
                @Override
                public void onCollectionUnregistered(String name) {}
            });
            
            // Add a listener that succeeds
            registry.addListener(new CollectionChangeListener() {
                @Override
                public void onCollectionRegistered(CollectionDefinition definition) {
                    successCount.incrementAndGet();
                }
                @Override
                public void onCollectionUpdated(CollectionDefinition oldDef, CollectionDefinition newDef) {}
                @Override
                public void onCollectionUnregistered(String name) {}
            });
            
            // Should not throw and should still notify second listener
            assertDoesNotThrow(() -> registry.register(createCollection("products")));
            assertEquals(1, successCount.get());
            
            // Collection should still be registered
            assertNotNull(registry.get("products"));
        }

        @Test
        @DisplayName("Should notify multiple listeners")
        void shouldNotifyMultipleListeners() {
            AtomicInteger listener1Count = new AtomicInteger(0);
            AtomicInteger listener2Count = new AtomicInteger(0);
            
            registry.addListener(new CollectionChangeListener() {
                @Override
                public void onCollectionRegistered(CollectionDefinition definition) {
                    listener1Count.incrementAndGet();
                }
                @Override
                public void onCollectionUpdated(CollectionDefinition oldDef, CollectionDefinition newDef) {}
                @Override
                public void onCollectionUnregistered(String name) {}
            });
            
            registry.addListener(new CollectionChangeListener() {
                @Override
                public void onCollectionRegistered(CollectionDefinition definition) {
                    listener2Count.incrementAndGet();
                }
                @Override
                public void onCollectionUpdated(CollectionDefinition oldDef, CollectionDefinition newDef) {}
                @Override
                public void onCollectionUnregistered(String name) {}
            });
            
            registry.register(createCollection("products"));
            
            assertEquals(1, listener1Count.get());
            assertEquals(1, listener2Count.get());
        }
    }

    @Nested
    @DisplayName("Helper Method Tests")
    class HelperMethodTests {

        @Test
        @DisplayName("size() should return number of collections")
        void sizeShouldReturnNumberOfCollections() {
            assertEquals(0, registry.size());
            
            registry.register(createCollection("products"));
            assertEquals(1, registry.size());
            
            registry.register(createCollection("orders"));
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("isEmpty() should return true when empty")
        void isEmptyShouldReturnTrueWhenEmpty() {
            assertTrue(registry.isEmpty());
            
            registry.register(createCollection("products"));
            assertFalse(registry.isEmpty());
        }

        @Test
        @DisplayName("contains() should check if collection exists")
        void containsShouldCheckIfCollectionExists() {
            assertFalse(registry.contains("products"));
            
            registry.register(createCollection("products"));
            assertTrue(registry.contains("products"));
            assertFalse(registry.contains("orders"));
        }

        @Test
        @DisplayName("getListenerCount() should return number of listeners")
        void getListenerCountShouldReturnNumberOfListeners() {
            assertEquals(0, registry.getListenerCount());
            
            CollectionChangeListener listener = new CollectionChangeListener() {
                @Override
                public void onCollectionRegistered(CollectionDefinition definition) {}
                @Override
                public void onCollectionUpdated(CollectionDefinition oldDef, CollectionDefinition newDef) {}
                @Override
                public void onCollectionUnregistered(String name) {}
            };
            
            registry.addListener(listener);
            assertEquals(1, registry.getListenerCount());
            
            registry.removeListener(listener);
            assertEquals(0, registry.getListenerCount());
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent reads safely")
        void shouldHandleConcurrentReadsSafely() throws InterruptedException, ExecutionException {
            // Pre-populate registry
            for (int i = 0; i < 100; i++) {
                registry.register(createCollection("collection" + i));
            }
            
            ExecutorService executor = Executors.newFixedThreadPool(10);
            List<Future<CollectionDefinition>> futures = new ArrayList<>();
            
            // Submit many concurrent read operations
            for (int i = 0; i < 1000; i++) {
                final int index = i % 100;
                futures.add(executor.submit(() -> registry.get("collection" + index)));
            }
            
            // All reads should succeed
            for (Future<CollectionDefinition> future : futures) {
                CollectionDefinition result = future.get();
                assertNotNull(result);
            }
            
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Should handle concurrent writes safely")
        void shouldHandleConcurrentWritesSafely() throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(100);
            
            // Submit many concurrent write operations
            for (int i = 0; i < 100; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        registry.register(createCollection("collection" + index));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            
            // All collections should be registered
            assertEquals(100, registry.size());
            for (int i = 0; i < 100; i++) {
                assertNotNull(registry.get("collection" + i));
            }
        }

        @Test
        @DisplayName("Should handle concurrent reads and writes safely")
        void shouldHandleConcurrentReadsAndWritesSafely() throws InterruptedException {
            ExecutorService executor = Executors.newFixedThreadPool(20);
            CountDownLatch latch = new CountDownLatch(200);
            AtomicInteger readSuccesses = new AtomicInteger(0);
            AtomicInteger writeSuccesses = new AtomicInteger(0);
            
            // Submit concurrent reads and writes
            for (int i = 0; i < 100; i++) {
                final int index = i;
                
                // Write operation
                executor.submit(() -> {
                    try {
                        registry.register(createCollection("collection" + index));
                        writeSuccesses.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
                
                // Read operation
                executor.submit(() -> {
                    try {
                        // Read any collection (may or may not exist yet)
                        registry.get("collection" + (index % 50));
                        registry.getAllCollectionNames();
                        readSuccesses.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            
            // All operations should complete successfully
            assertEquals(100, writeSuccesses.get());
            assertEquals(100, readSuccesses.get());
            assertEquals(100, registry.size());
        }

        @Test
        @DisplayName("Should maintain consistency during concurrent updates to same collection")
        void shouldMaintainConsistencyDuringConcurrentUpdates() throws InterruptedException {
            // Register initial collection
            registry.register(createCollectionWithVersion("products", 1L));
            
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(100);
            
            // Submit many concurrent updates to the same collection
            for (int i = 0; i < 100; i++) {
                executor.submit(() -> {
                    try {
                        registry.register(createCollectionWithVersion("products", 1L));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            
            // Should still have exactly one collection
            assertEquals(1, registry.size());
            
            // Version should have been incremented (at least once)
            CollectionDefinition result = registry.get("products");
            assertNotNull(result);
            assertTrue(result.version() > 1L);
        }
    }
}
