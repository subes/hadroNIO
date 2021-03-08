package de.hhu.bsinfo.hadronio.benchmark.latency;

import de.hhu.bsinfo.hadronio.UcxProvider;
import de.hhu.bsinfo.hadronio.util.LatencyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

@CommandLine.Command(
        name = "latency",
        description = "Messaging latency benchmark.",
        showDefaultValues = true,
        separator = " ")
public class LatencyBenchmark implements Runnable {

    static {
        System.setProperty("java.nio.channels.spi.SelectorProvider", "de.hhu.bsinfo.hadronio.UcxProvider");
        UcxProvider.printBanner();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(LatencyBenchmark.class);
    private static final int DEFAULT_SERVER_PORT = 2998;

    @CommandLine.Option(
            names = {"-s", "--server"},
            description = "Run this instance in server mode.")
    private boolean isServer = false;

    @CommandLine.Option(
            names = {"-a", "--address"},
            description = "The address to bind to.")
    private InetSocketAddress bindAddress = new InetSocketAddress(DEFAULT_SERVER_PORT);

    @CommandLine.Option(
            names = {"-r", "--remote"},
            description = "The address to connect to.")
    private InetSocketAddress remoteAddress;

    @CommandLine.Option(
            names = {"-b", "--blocking"},
            description = "Use blocking channels.")
    private boolean blocking = false;

    @CommandLine.Option(
            names = {"-l", "--length"},
            description = "The message size.")
    private int messageSize = 1024;

    @CommandLine.Option(
            names = {"-c", "--count"},
            description = "The amount of messages.")
    private int messageCount = 1000;

    private SocketChannel socket;
    private ByteBuffer messageBuffer;
    private LatencyResult result;

    @Override
    public void run() {
        if (!isServer && remoteAddress == null) {
            LOGGER.error("Please specify the server address");
            return;
        }

        bindAddress = isServer ? bindAddress : new InetSocketAddress(bindAddress.getAddress(), 0);
        messageBuffer = ByteBuffer.allocateDirect(messageSize);

        try {
            if (isServer) {
                result = new LatencyResult(messageCount, messageSize);
                final ServerSocketChannel serverSocket = ServerSocketChannel.open();
                serverSocket.configureBlocking(true);
                serverSocket.bind(bindAddress);
                socket = serverSocket.accept();
                serverSocket.close();
            } else {
                socket = SocketChannel.open(remoteAddress);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to create socket channel!", e);
            return;
        }

        try {
            if (blocking) {
                runBlocking();
            } else {
                runNonBlocking();
            }

            socket.close();
        } catch (IOException e) {
            LOGGER.error("Benchmark failed!", e);
            return;
        }

        if (isServer) {
            LOGGER.info("Benchmark result:\n{}", result);
        }
    }

    private void runBlocking() throws IOException {
        LOGGER.info("Starting benchmark with blocking socket channels");
        socket.configureBlocking(true);

        if (isServer) {
            final long startTime = System.nanoTime();

            for (int i = 0; i < messageCount; i++) {
                result.startSingleMeasurement();
                socket.write(messageBuffer);
                messageBuffer.flip();

                do {
                    socket.read(messageBuffer);
                } while (messageBuffer.hasRemaining());

                result.stopSingleMeasurement();
                messageBuffer.flip();
            }

            result.finishMeasuring(System.nanoTime() - startTime);
        } else {
            for (int i = 0; i < messageCount; i++) {
                do {
                    socket.read(messageBuffer);
                } while (messageBuffer.hasRemaining());

                messageBuffer.flip();
                socket.write(messageBuffer);
                messageBuffer.flip();
            }
        }

    }

    private void runNonBlocking() throws IOException {
        LOGGER.info("Starting benchmark with non-blocking socket channels");
        socket.configureBlocking(false);

        final Selector selector = Selector.open();
        if (isServer) {
            final SelectionKey key = socket.register(selector, SelectionKey.OP_WRITE);
            key.attach(new ServerHandler(socket, key, messageBuffer, messageCount, result));
        } else {
            final SelectionKey key = socket.register(selector, SelectionKey.OP_READ);
            key.attach(new ClientHandler(socket, key, messageBuffer, messageCount));
        }

        final long startTime = System.nanoTime();

        while (!selector.keys().isEmpty()) {
            selector.selectNow();

            for (final SelectionKey key : selector.selectedKeys()) {
                ((Runnable) key.attachment()).run();
            }

            selector.selectedKeys().clear();
        }

        if (isServer) {
            result.finishMeasuring(System.nanoTime() - startTime);
        }
    }
}