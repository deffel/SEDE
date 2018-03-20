package de.upb.sede.requests;

/**
 * All instances of request should be immutable.
 */
public abstract class Request {
    public abstract String getRequestId();
    public abstract String getClientId();
    public abstract String[] getClientHostSet();
}