package de.hhu.bsinfo.hadronio.benchmark.throughput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class ClientHandler implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientHandler.class);

    private final SocketChannel socket;
    private final SelectionKey key;
    private final ByteBuffer messageBuffer;

    private int remainingMessages;

    public ClientHandler(final SocketChannel socket, final SelectionKey key, final ByteBuffer messageBuffer, final int messageCount) {
        this.socket = socket;
        this.key = key;
        this.messageBuffer = messageBuffer;
        remainingMessages = messageCount;
    }

    @Override
    public void run() {
        try {
            socket.read(messageBuffer);
        } catch (IOException e) {
            LOGGER.error("Failed to receive a message!");
        }

        if (!messageBuffer.hasRemaining()) {
            messageBuffer.clear();
            remainingMessages--;
        }

        if (remainingMessages <= 0) {
            key.cancel();
        }
    }
}
