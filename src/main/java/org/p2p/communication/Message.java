package org.p2p.communication;

import org.communication.enums.MessageType;

import lombok.Getter;

@Getter
public class Message {

    public static final String END_MESSAGE = "<END>";
    public static final String NEW_LINE = "\n";

    private final String from;

    private final String content;

    private final MessageType type;

    public Message(String from, String content) {
        this.type = MessageType.MESSAGE;
        this.from = from;
        this.content = content;
    }

    public Message(MessageType type, String from, String content) {
        this.type = type;
        this.from = from;
        this.content = content;
    }

    @Override
    public String toString() {
        StringBuilder message = new StringBuilder();

        message.append(this.type.getCode());
        message.append(NEW_LINE);
        message.append(this.from);
        message.append(NEW_LINE);
        message.append(this.content);
        message.append(NEW_LINE);
        message.append(END_MESSAGE);

        return message.toString();
    }

}
