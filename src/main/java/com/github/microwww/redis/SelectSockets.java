package com.github.microwww.redis;

import com.github.microwww.redis.exception.Run;
import com.github.microwww.redis.logger.LogFactory;
import com.github.microwww.redis.logger.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public abstract class SelectSockets implements Closeable {
    private static final Logger logger = LogFactory.getLogger(SelectSockets.class);

    private ServerSocketChannel serverChannel;
    protected ServerSocket serverSocket;
    protected Selector selector;
    private final List<Consumer<SelectSockets>> closeHandlers = new ArrayList<>();
    private boolean close = false;

    public Runnable config(String host, int port) throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverSocket = serverChannel.socket();
        selector = Selector.open();
        serverSocket.bind(new InetSocketAddress(host, port));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        return () -> {
            Thread.currentThread().setName("SELECT-IO");
            while (true) {
                if (close) break;
                Run.ignoreException(logger, () -> {// start LISTENER
                    this.tryRun();
                });
            }
            Run.ignoreException(logger, this::clean);
        };
    }

    public void tryRun() throws IOException {
        int n = selector.select(1000);
        if (n == 0) {
            return;
        }
        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
        while (it.hasNext()) {
            SelectionKey key = it.next();
            try {
                if (key.isValid()) { // CancelledKeyException
                    if (key.isAcceptable()) {
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel channel = server.accept();
                        this.acceptHandler(channel);
                    }
                    if (key.isReadable()) {
                        readableHandler(key);
                    }
                    if(key.isConnectable()){
                        System.out.println("connection");
                    }
                }
            } catch (IOException ex) { // ?????????????????????????????????
                logger.debug("close client");
                closeChannel(key);
            }
            it.remove();
        }
    }

    protected void registerChannel(SelectableChannel channel, int ops) throws IOException {
        if (channel == null) {
            return; // could happen
        }
        channel.configureBlocking(false);
        channel.register(selector, ops);
    }

    protected void readableHandler(SelectionKey key) {
    }

    protected void acceptHandler(SocketChannel channel) throws IOException {
        registerChannel(channel, SelectionKey.OP_READ);
    }

    public void closeChannel(SelectionKey key) throws IOException {
        this.closeChannel((SocketChannel) key.channel());
    }

    public void closeChannel(SocketChannel channel) throws IOException {
        try {
            InetSocketAddress add = (InetSocketAddress) channel.getRemoteAddress();
            logger.info("Remote KILLED: {}:{}", add.getHostName(), add.getPort());
        } catch (Exception e) {
            logger.info("Remote KILLED : {}", channel);
        }
        channel.close();
    }

    public ServerSocket getServerSocket() {
        if (serverChannel == null) {
            Thread.yield();
        }
        return serverSocket;
    }

    protected Iterator<Consumer<SelectSockets>> getCloseHandlers() {
        return closeHandlers.iterator();
    }

    public void addCloseHandlers(Consumer<SelectSockets> close) {
        closeHandlers.add(close);
    }

    @Override
    public void close() throws IOException {
        if (close) {
            return;
        }
        this.close = true;
    }

    protected void clean() throws IOException {
        this.close = true;
        Thread.yield();
        if (selector != null)
            selector.close();
        if (serverSocket != null)
            serverSocket.close();
        Iterator<Consumer<SelectSockets>> its = getCloseHandlers();
        while (its.hasNext()) {
            try {
                Consumer<SelectSockets> next = its.next();
                next.accept(this);
            } catch (RuntimeException ex) {
                logger.error("Close error , SKIP", ex);
            }
        }
    }
}