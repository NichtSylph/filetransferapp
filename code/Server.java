package filetransferappjs;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

public class Server {
    public static void main(String[] args) throws NoSuchAlgorithmException {
        if (args.length < 1) {
            System.out.println("Usage: java Server <Port>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        int windowSize = 32;
        try (Selector selector = Selector.open();
             ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {

            serverSocketChannel.bind(new InetSocketAddress(port));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Server is listening on port " + port);

            while (true) {
                selector.select();
                Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();

                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();

                    if (key.isAcceptable()) {
                        SocketChannel clientSocketChannel = serverSocketChannel.accept();
                        clientSocketChannel.configureBlocking(false);
                        clientSocketChannel.register(selector, SelectionKey.OP_READ);

                        System.out.println("Client connected");
                        ServerSession serverSession = new ServerSession(clientSocketChannel, windowSize);
                        try {
                            serverSession.startSession();
                        } catch (Exception e) {
                            System.out.println("Error in session: " + e.getMessage());
                        }
                    }

                    keyIterator.remove();
                }
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
        }
    }
}