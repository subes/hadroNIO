package de.hhu.bsinfo.hadronio.example.netty.benchmark.latency;

import de.hhu.bsinfo.hadronio.util.LatencyResult;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerHandler.class);

    private final int messageSize;
    private final int messageCount;
    private final LatencyResult result;

    private int receivedMessages = 0;
    private int receivedBytes = 0;
    private long startTime;

    private final ByteBuf sendBuffer;

    public ServerHandler(final int messageSize, final int messageCount, final ByteBuf sendBuffer) {
        this.messageSize = messageSize;
        this.messageCount = messageCount;
        this.sendBuffer = sendBuffer;
        result = new LatencyResult(messageCount, messageSize);
    }

    public void start(final ChannelHandlerContext context) {
        LOGGER.info("Starting benchmark with [{}] messages", messageCount);
        startTime = System.nanoTime();
        result.startSingleMeasurement();
        context.channel().writeAndFlush(sendBuffer);
    }

    @Override
    public void channelRead(final ChannelHandlerContext context, final Object message) {
        final ByteBuf receiveBuffer = (ByteBuf) message;
        receivedBytes += receiveBuffer.readableBytes();
        receiveBuffer.release();

        if (receivedBytes == messageSize) {
            result.stopSingleMeasurement();
            sendBuffer.resetReaderIndex();
            receivedBytes = 0;
            receivedMessages++;

            if (receivedMessages < messageCount) {
                result.startSingleMeasurement();
                context.channel().writeAndFlush(sendBuffer);
            } else {
                result.finishMeasuring(System.nanoTime() - startTime);
                LOGGER.info(result.toString());
                context.channel().close();
            }
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
        LOGGER.error("An exception occurred", cause);
        context.channel().close();
    }
}
