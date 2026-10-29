package com.websocketlab.server;

public class ClientInfo {
    private String sessionId;
    private String serverId;
    private long connectedAt;

    public ClientInfo() {}

    public ClientInfo(String sessionId, String serverId, long connectedAt) {
        this.sessionId = sessionId;
        this.serverId = serverId;
        this.connectedAt = connectedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(long connectedAt) {
        this.connectedAt = connectedAt;
    }
}
