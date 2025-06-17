package org.p2p;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

@Getter
public class Message implements Serializable {

    public enum Type {
        ANNOUNCE, FILE_REQUEST,
        FILE_RESPONSE, CHOKE, UNCHOKE
    }

    public enum DataType {
        IP, PORT, SUCCESS, REASON, FILES,
        FILE_NAME, FILE_DATA, FILES_PER_PEER,
    }

    private final Type type;
    private final String senderId;
    private final Map<DataType, Object> data;

    public Message(Type type, String senderId) {
        this.type = type;
        this.senderId = senderId;
        this.data = new HashMap<>();
    }

    public void addData(DataType dataType, Object value) {
        data.put(dataType, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getData(DataType dataType) {
        return (T) data.get(dataType);
    }
}
