package network.ike.docs.koncept;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks references to a single Koncept within a document.
 * Thread-safe for concurrent macro processing.
 */
public class KonceptEntry {

    private final String identifier;
    private final AtomicInteger refCount = new AtomicInteger(0);

    public KonceptEntry(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public int incrementRefCount() {
        return refCount.incrementAndGet();
    }

    public int getRefCount() {
        return refCount.get();
    }

    @Override
    public String toString() {
        return "KonceptEntry{id='%s', refs=%d}".formatted(identifier, refCount.get());
    }
}
