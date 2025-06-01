package org.p2p.communication;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;

import lombok.Setter;

public abstract class Messenger {

    protected final Socket socket;

    protected final InputStream inputStream;

    protected final Scanner input;

    protected final PrintStream output;

    @Setter
    private boolean logged = true;

    public Messenger(String host, int port) throws IOException {
        Socket socket = new Socket(host, port);

        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.input = new Scanner(this.inputStream);
        this.output = new PrintStream(socket.getOutputStream(), true);
    }

    public void close() throws IOException {
        if (this.socket.isClosed()) return;

        this.output.close();
        this.inputStream.close();
        this.input.close();
        this.socket.close();
    }

    protected void log(String message) {
        if (!this.logged) return;
        System.out.println(message);
    }

}
