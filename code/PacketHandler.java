package filetransferappjs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Set;

public class PacketHandler {
    private final SocketChannel socketChannel;
    private final Selector selector;

    public PacketHandler(SocketChannel socketChannel) throws IOException {
        this.socketChannel = socketChannel;
        this.selector = Selector.open();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
    }

    public void sendPacket(Packet packet) throws IOException {
        byte[] packetBytes = packet.toBytes();
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + packetBytes.length);
        buffer.putInt(packetBytes.length);
        buffer.put(packetBytes);
        buffer.flip();
        while (buffer.hasRemaining()) {
            socketChannel.write(buffer);
        }
       // System.out.println("Sent packet: " + packet.getSequenceNumber());
    }

    public Packet receivePacket() throws IOException {
        ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);
        while (lengthBuffer.hasRemaining()) {
            if (socketChannel.read(lengthBuffer) == -1) {
                throw new IOException("End of stream reached");
            }
        }
        lengthBuffer.flip();
        int packetLength = lengthBuffer.getInt();

        if (packetLength > 0) {
            ByteBuffer packetBuffer = ByteBuffer.allocate(packetLength);
            while (packetBuffer.hasRemaining()) {
                if (socketChannel.read(packetBuffer) == -1) {
                    throw new IOException("End of stream reached");
                }
            }
            packetBuffer.flip();
            return Packet.fromByteBuffer(packetBuffer);
        }
        return null;
    }

    public void sendAck(int sequenceNumber, int sessionId) throws IOException {
        Packet ackPacket = new Packet(OpCode.ACK, sequenceNumber, sessionId, new byte[0]);
        sendPacket(ackPacket);
    }

    public Packet receiveAck(long timeout) throws IOException {
        while (true) {
            int readyChannels = selector.select(timeout);
            if (readyChannels == 0) {
                return null; // Timeout expired
            }

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            for (SelectionKey key : selectedKeys) {
                if (key.isReadable()) {
                    Packet packet = receivePacket();
                    if (packet != null && (packet.getOpCode() == OpCode.ACK || packet.getOpCode() == OpCode.OACK || packet.getOpCode() == OpCode.END_OF_TRANSFER)) {
                        selectedKeys.remove(key);
                        return packet;
                    }
                }
            }
        }
    }
}