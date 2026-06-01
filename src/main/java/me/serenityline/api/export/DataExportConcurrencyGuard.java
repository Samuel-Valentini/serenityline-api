package me.serenityline.api.export;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DataExportConcurrencyGuard {

    private final Semaphore globalSemaphore;
    private final ConcurrentHashMap<UUID, Boolean> runningUsers = new ConcurrentHashMap<>();

    public DataExportConcurrencyGuard(DataExportProperties properties) {
        Objects.requireNonNull(properties, "properties");

        this.globalSemaphore = new Semaphore(properties.maxConcurrentExports());
    }

    public Permit acquire(UUID userId) {
        Objects.requireNonNull(userId, "userId");

        if (runningUsers.putIfAbsent(userId, Boolean.TRUE) != null) {
            throw new IllegalStateException("export.alreadyRunning");
        }

        boolean globalPermitAcquired = globalSemaphore.tryAcquire();

        if (!globalPermitAcquired) {
            runningUsers.remove(userId);
            throw new IllegalStateException("export.temporarilyBusy");
        }

        return new Permit(userId);
    }

    public final class Permit implements AutoCloseable {

        private final UUID userId;
        private final AtomicBoolean released = new AtomicBoolean(false);

        private Permit(UUID userId) {
            this.userId = userId;
        }

        @Override
        public void close() {
            if (!released.compareAndSet(false, true)) {
                return;
            }

            runningUsers.remove(userId);
            globalSemaphore.release();
        }
    }
}