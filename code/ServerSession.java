package filetransferappjs;

import java.io.*;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Queue;

public class ServerSession {
    private final PacketHandler packetHandler;
    private final SlidingWindow slidingWindow;
    private int sessionId;
    private byte[] key;

    public ServerSession(SocketChannel socketChannel, int defaultWindowSize) throws IOException {
        this.packetHandler = new PacketHandler(socketChannel);
        this.slidingWindow = new SlidingWindow(defaultWindowSize);
    }

    public void startSession() throws IOException, NoSuchAlgorithmException {
        while (true) { // Keep the server running
            // Reset session state if necessary
            this.sessionId = -1;
            this.key = null;

            // Initiate session including window size negotiation
            initiateSession();

            // Main loop for client requests
            boolean sessionEnded = false;
            while (!sessionEnded) {
                Packet requestPacket = packetHandler.receivePacket();
                if (requestPacket == null) {
                    // Handle null packet (e.g., client disconnected)
                    System.out.println("Client disconnected.");
                    break; // Exit the inner loop to reset session or accept a new connection
                }

                switch (requestPacket.getOpCode()) {
                    case ACK:
                        // Simply log or ignore ACKs received after END_OF_TRANSFER
                        System.out.println("ACK received for packet: " + requestPacket.getSequenceNumber());
                        break;
                    case RRQ:
                        handleDownload(requestPacket.getFileName());
                        break;
                    case WRQ:
                        handleUpload(requestPacket.getFileName());
                        break;
                    case OACK:
                        handleOack(requestPacket);
                        break;
                    case END_OF_TRANSFER:
                        System.out.println("End of transfer and session received.");
                        sessionEnded = true; // End the current session, but keep the server running
                        break;
                    default:
                        System.out.println("Unsupported operation: " + requestPacket.getOpCode());
                        break;
                }
            }
        }
    }

    private void initiateSession() throws IOException {
        Packet sessionStartPacket = packetHandler.receivePacket();
        if (sessionStartPacket != null && sessionStartPacket.getOpCode() == OpCode.SESSION_START) {
            this.sessionId = Integer.parseInt(sessionStartPacket.getOptions().get("sessionId"));
            this.key = parseKeyString(sessionStartPacket.getOptions().get("key"));
            System.out.println("Session initiated with ID: " + sessionId + "Key Exchange Succesful.");
        } else {
            throw new IOException("Expected SESSION_START packet");
        }
    }

    private void handleOack(Packet oackPacket) throws IOException {
        if (oackPacket.getOptions().containsKey("windowSize")) {
            int clientWindowSize = Integer.parseInt(oackPacket.getOptions().get("windowSize"));
            slidingWindow.setWindowSize(clientWindowSize);
            System.out.println("Adjusted window size to: " + clientWindowSize);
        }

        // Send a message to the client indicating that the server has adjusted its
        // window size
        Packet ackPacket = new Packet(OpCode.ACK, 0, sessionId,
                ("Adjusted window size to: " + slidingWindow.getWindowSize()).getBytes());
        packetHandler.sendPacket(ackPacket);
    }

    private void handleUpload(String filename) throws IOException {
        String uniqueFilePath = FileUtil.writeFile(filename, new byte[0], true); // Initialize file

        try (FileOutputStream fos = new FileOutputStream(uniqueFilePath, true)) { // Append mode
            boolean fileTransferComplete = false;

            while (!fileTransferComplete) {
                Packet dataPacket = packetHandler.receivePacket();

                if (dataPacket == null) {
                    System.out.println("Unexpected end of connection.");
                    break;
                }

                if (dataPacket.getOpCode() == OpCode.END_OF_TRANSFER) {
                    slidingWindow.acknowledgePacket(dataPacket.getSequenceNumber());
                    packetHandler.sendAck(dataPacket.getSequenceNumber(), sessionId);
                    fileTransferComplete = true;
                } else {
                    byte[] decryptedData = EncryptionUtil.xorEncryptDecrypt(dataPacket.getData(), key);
                    fos.write(decryptedData);
                    slidingWindow.acknowledgePacket(dataPacket.getSequenceNumber());
                    packetHandler.sendAck(dataPacket.getSequenceNumber(), sessionId);
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing uploaded file: " + e.getMessage());
        }

        System.out.println("Upload of '" + filename + "' completed.");
    }

    private void handleDownload(String filename) throws IOException {
        String filePath = FileUtil.SERVER_DIR + File.separator + filename;

        try (InputStream fileStream = Files.newInputStream(Paths.get(filePath))) {
            byte[] buffer = new byte[Packet.MAX_PACKET_SIZE - Packet.HEADER_SIZE];
            int bytesRead;
            while ((bytesRead = fileStream.read(buffer)) != -1) {
                byte[] dataChunk = Arrays.copyOf(buffer, bytesRead); // Copy only the bytes that were read
                byte[] encryptedContent = EncryptionUtil.xorEncryptDecrypt(dataChunk, key);

                Packet dataPacket = new Packet(OpCode.DATA, slidingWindow.getNextSequenceNumber(), sessionId,
                        encryptedContent);
                slidingWindow.queuePacket(dataPacket);

                packetHandler.sendPacket(dataPacket);
            }
        } catch (IOException ex) {
            System.out.println("Error during file download: " + ex.getMessage());
        }

        // Handle retransmissions
        Queue<Packet> packetsForRetransmission = slidingWindow.getPacketsForRetransmission();
        for (Packet packet : packetsForRetransmission) {
            packetHandler.sendPacket(packet);
        }

        Packet endOfTransferPacket = new Packet(OpCode.END_OF_TRANSFER, slidingWindow.getNextSequenceNumber(),
                sessionId, new byte[0]);
        packetHandler.sendPacket(endOfTransferPacket);

        System.out.println("File download completed and END_OF_TRANSFER packet sent for: " + filename);
    }

    private byte[] parseKeyString(String keyStr) {
        keyStr = keyStr.substring(1, keyStr.length() - 1); // Remove brackets
        String[] byteValues = keyStr.split(",\\s*");
        byte[] bytes = new byte[byteValues.length];
        for (int i = 0; i < byteValues.length; i++) {
            bytes[i] = Byte.parseByte(byteValues[i]);
        }
        return bytes;
    }
}
