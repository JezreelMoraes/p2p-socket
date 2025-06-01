package org.p2p.communication;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import org.p2p.communication.enums.MessageType;

import lombok.NonNull;

public class TcpMessenger extends Messenger {

    public TcpMessenger(String host, int port) throws IOException {
        super(host, port);
    }

    public void waitMessageReceive(@NonNull Function<Message, Void> messageListener) {
        while (!this.socket.isClosed() && this.input.hasNextLine()) {
            try {
                Message message = MessageBuilder.buildMessage(input);

                if (message != null) {
                    log(">> Recebendo mensagem:\n" + message.getContent());
                    messageListener.apply(message);
                }
            } catch (Exception exception) {
                log("waitMessageReceive >> Houve um problema de conexão com o servidor, Erro: " + exception);
            }
        }
    }

    public Message waitSingleMessageReceive() {
        try {
            if (this.input.hasNextLine()) {
                Message message = MessageBuilder.buildMessage(input);
                if (message != null) {
                    log(">> Recebendo mensagem:\n" + message.getContent());
                    return message;
                }
            }
        } catch (Exception exception) {
            log("waitSingleMessageReceive >> Houve um problema de conexão com o servidor, Erro: " + exception);
        }

        return null;
    }

    public void sendMessage(String from, String content) {
        sendMessage(new Message(from, content));
    }

    public void sendMessage(Message message) {
        this.output.println(message);
    }

    public void sendFile(String filepath) {
        DataOutputStream dataOutputStream;
        FileInputStream fileInputStream = null;

        try {
            File fileToSend = new File(filepath);

            if (!fileToSend.exists() && !fileToSend.createNewFile()) {
                log("sendFile >> Não foi possível encontrar ou criar o arquivo");
                return;
            }

            dataOutputStream = new DataOutputStream(this.output);
            dataOutputStream.writeUTF(fileToSend.getName());
            dataOutputStream.writeLong(fileToSend.length());

            fileInputStream = new FileInputStream(fileToSend);
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesSent = 0;

            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                dataOutputStream.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;

                double progress = (double) totalBytesSent / fileToSend.length() * 100;
                System.out.printf("sendFile > Enviando: %s - Progresso: %.2f%%\n", fileToSend.getName(), progress);
            }

            dataOutputStream.flush();
            log("sendFile >> Envio concluído!");

        } catch (Exception exception) {
            log("sendFile > ERRO: " + exception);
            try {
                new DataOutputStream(this.output).writeUTF(MessageType.ERROR.getCode());
            } catch (IOException ignored) {
            }
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    public boolean receiveFile(Path receivePath) {
        try {
            DataInputStream dataIn = new DataInputStream(this.inputStream);

            String possibleErrorOrFilename = dataIn.readUTF();

            if (MessageType.ERROR.getCode().equals(possibleErrorOrFilename)) {
                log("receiveFile > Ocorreu um erro no recebimento do arquivo.");
                return false;
            }

            long fileSize = dataIn.readLong();

            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalRead = 0;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            while (totalRead < fileSize && (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                baos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }

            byte[] fileData = baos.toByteArray();

            if (Files.notExists(receivePath)) {
                Files.createDirectories(receivePath);
            }

            Path newFilepath = receivePath.resolve(possibleErrorOrFilename);
            Files.write(newFilepath, fileData);

            log("receiveFile > Arquivo criado com sucesso!");
            return true;
        } catch (Exception exception) {
            log("receiveFile > Erro ao receber arquivo: " + exception);
            return false;
        }
    }

}
