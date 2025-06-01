package org.p2p.communication;

import java.util.Scanner;

import org.communication.enums.MessageType;

public class MessageBuilder {

    public static Message buildMessage(Scanner scanner) {
        String messageTypeString = scanner.nextLine();
        MessageType messageType = MessageType.convert(messageTypeString);

        if (messageType == null) return null;

        String from = scanner.nextLine();

        StringBuilder messageContent = new StringBuilder();
        String line = scanner.nextLine();
        while (!line.equals(Message.END_MESSAGE)) {
            messageContent.append(line);
            messageContent.append(Message.NEW_LINE);

            line = scanner.nextLine();
        }

        return new Message(messageType, from, messageContent.toString().trim());
    }
}
