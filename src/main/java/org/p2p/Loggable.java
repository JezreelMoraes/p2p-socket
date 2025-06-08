package org.p2p;

abstract class Loggable {

    protected void logInfo(String message) {
        System.out.println(buildLogMessage(message));
    }

    protected void logError(String message) {
        System.err.println(buildLogMessage(message));
    }

    protected String buildLogMessage(String message) {
        return buildInfo() + message;
    }

    abstract String buildInfo();

}
