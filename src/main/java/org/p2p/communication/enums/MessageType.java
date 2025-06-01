package org.p2p.communication.enums;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum MessageType {

    MESSAGE("<MESSAGE>"),
    ERROR("<ERROR>"),
    FILE("<FILE>");

    private final String code;

    private MessageType(String code) {
        this.code = code;
    }

    public static MessageType convert(String code) {
        return Arrays.stream(MessageType.values())
            .filter(messageType -> messageType.code.equals(code))
            .findFirst()
            .orElse(null);
    }

}
