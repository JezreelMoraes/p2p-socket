package org.p2p;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

@Getter
public class Message implements Serializable {

    public enum Type {
        REGISTER, ANNOUNCE, REQUEST_PEERS, PEER_LIST, FILE_REQUEST,
        FILE_RESPONSE, CHOKE, UNCHOKE, INTERESTED, NOT_INTERESTED,
    }

    private final Type type;
    private final String senderId;
    private final Map<String, Object> data;

    public Message(Type type, String senderId) {
        this.type = type;
        this.senderId = senderId;
        this.data = new HashMap<>();
    }

    public void addData(String key, Object value) {
        data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(String key) {
        return (T) data.get(key);
    }
}
