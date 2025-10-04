package com.messagecheck.velocity;

import org.slf4j.Logger;

import java.util.logging.Level;
import java.util.logging.LogRecord;

public final class VelocityJavaLogger extends java.util.logging.Logger {
    private final Logger delegate;

    public VelocityJavaLogger(Logger delegate) {
        super(delegate.getName(), null);
        this.delegate = delegate;
    }

    @Override
    public void log(LogRecord record) {
        Level level = record.getLevel();
        String message = record.getMessage();
        Throwable throwable = record.getThrown();
        if (level.intValue() >= Level.SEVERE.intValue()) {
            delegate.error(message, throwable);
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            delegate.warn(message, throwable);
        } else if (level.intValue() >= Level.INFO.intValue()) {
            delegate.info(message);
        } else {
            delegate.debug(message);
        }
    }
}
