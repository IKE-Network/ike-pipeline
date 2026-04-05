package network.ike.plugin.ws;

import org.apache.maven.plugin.logging.Log;

/**
 * A {@link Log} wrapper that captures info-level output to a
 * StringBuilder while delegating all calls to the real logger.
 *
 * <p>Goals that want report output wrap their logger at the start
 * of {@code execute()} and call {@link #captured()} at the end.
 * Only {@code info} messages are captured — debug/warn/error go
 * straight through without capture.
 *
 * <p>Thread-safe: the StringBuilder is local to each Mojo instance.
 */
final class ReportLog implements Log {

    private final Log delegate;
    private final StringBuilder captured = new StringBuilder();

    ReportLog(Log delegate) {
        this.delegate = delegate;
    }

    /** Return the captured info-level output as a string. */
    String captured() {
        return captured.toString();
    }

    // ── Info (captured) ─────────────────────────────────────────────

    @Override public boolean isInfoEnabled() { return delegate.isInfoEnabled(); }

    @Override
    public void info(CharSequence content) {
        delegate.info(content);
        captured.append(content).append('\n');
    }

    @Override
    public void info(CharSequence content, Throwable error) {
        delegate.info(content, error);
        captured.append(content).append('\n');
    }

    @Override
    public void info(Throwable error) {
        delegate.info(error);
    }

    // ── Debug (pass-through) ────────────────────────────────────────

    @Override public boolean isDebugEnabled() { return delegate.isDebugEnabled(); }
    @Override public void debug(CharSequence content) { delegate.debug(content); }
    @Override public void debug(CharSequence content, Throwable error) { delegate.debug(content, error); }
    @Override public void debug(Throwable error) { delegate.debug(error); }

    // ── Warn (pass-through) ─────────────────────────────────────────

    @Override public boolean isWarnEnabled() { return delegate.isWarnEnabled(); }
    @Override public void warn(CharSequence content) { delegate.warn(content); }
    @Override public void warn(CharSequence content, Throwable error) { delegate.warn(content, error); }
    @Override public void warn(Throwable error) { delegate.warn(error); }

    // ── Error (pass-through) ────────────────────────────────────────

    @Override public boolean isErrorEnabled() { return delegate.isErrorEnabled(); }
    @Override public void error(CharSequence content) { delegate.error(content); }
    @Override public void error(CharSequence content, Throwable error) { delegate.error(content, error); }
    @Override public void error(Throwable error) { delegate.error(error); }
}
