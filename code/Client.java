package filetransferappjs;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;

public class Client {
    public static void main(String[] args) throws NoSuchAlgorithmException {
        if (args.length < 6) {
            System.out.println("Usage: java Client <address> <port> <upload/download> <filename> <windowSize> [dropPackets]");
            return;
        }

        String address = args[0];
        int port = Integer.parseInt(args[1]);
        String mode = args[2].toLowerCase();
        String filename = args[3];
        int windowSize = Integer.parseInt(args[4]);
        boolean dropPackets = Boolean.parseBoolean(args[5]);

        try (SocketChannel socketChannel = SocketChannel.open()) {
            socketChannel.connect(new InetSocketAddress(address, port));
            socketChannel.configureBlocking(false);
            new ClientSession(socketChannel, mode, filename, windowSize, dropPackets);
        } catch (IOException e) {
            System.out.println("Client exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
