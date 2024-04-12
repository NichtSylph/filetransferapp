package filetransferappjs;

import java.io.*;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.logging.*;

public class ClientSession {
    private final PacketHandler packetHandler;
    private final String mode;
    private final String filename;
    private final boolean dropPackets;
    private final SlidingWindow slidingWindow;
    private int windowSize;
    private int sessionId;
    private byte[] key;
    private final Random random = new Random();
    private long startTransferTime;
    private static final Logger logger = Logger.getLogger(ClientSession.class.getName());

    public ClientSession(SocketChannel socketChannel, String mode, String filename, int windowSize, boolean dropPackets)
            throws IOException, NoSuchAlgorithmException {
        this.packetHandler = new PacketHandler(socketChannel);
        this.mode = mode;
        this.filename = filename;
        this.windowSize = windowSize;
        this.dropPackets = dropPackets;
        this.slidingWindow = new SlidingWindow(windowSize);
        setupLogger();
        startSession();
    }

    private void setupLogger() throws IOException {
        String logDirectoryPath = "C:\\Users\\joels\\OneDrive\\Oswego\\Spring 2024\\CSC445\\project2js\\filetransferappjs\\src\\main\\java\\filetransferappjs\\log\\";
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String logFilePath = logDirectoryPath + "throughput_" + timestamp + ".log";
        FileHandler fileHandler = new FileHandler(logFilePath, true); // Append to existing log

        // Set the log format to just the message itself
        fileHandler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        });

        logger.addHandler(fileHandler); // Add the file handler to the logger
    }

    private void startTransferTimer() {
        startTransferTime = System.nanoTime(); // Record the start time
    }

    private void displayThroughput(long transferredBytes) {
        long endTransferTime = System.nanoTime();
        double durationInSeconds = (endTransferTime - startTransferTime) / 1_000_000_000.0; // Convert nanoseconds to
                                                                                            // seconds
        double throughput = transferredBytes / durationInSeconds; // Bytes per second

        String logMessage = String.format("%.2f", throughput);
        logger.info(logMessage);
    }

    private void startSession() throws IOException, NoSuchAlgorithmException {
        initiateSession();
        negotiateWindowSize();

        String filePath = null;
        if ("upload".equals(mode)) {
            filePath = uploadFile();
        } else if ("download".equals(mode)) {
            filePath = downloadFile();
        }

        if (filePath != null) {
            validateFile(filePath);
        }
    }

    private void initiateSession() throws IOException {
        this.sessionId = new Random().nextInt();
        this.key = EncryptionUtil.generateKey(sessionId, System.currentTimeMillis());

        Map<String, String> sessionStartOptions = new HashMap<>();
        sessionStartOptions.put("sessionId", Integer.toString(sessionId));
        sessionStartOptions.put("key", Arrays.toString(key));

        Packet sessionStartPacket = new Packet(OpCode.SESSION_START, sessionStartOptions);
        packetHandler.sendPacket(sessionStartPacket);
    }

    private void negotiateWindowSize() throws IOException {
        // Send desired window size to the server
        Map<String, String> options = new HashMap<>();
        options.put("windowSize", String.valueOf(this.windowSize));

        Packet windowSizePacket = new Packet(OpCode.OACK, options);
        packetHandler.sendPacket(windowSizePacket);

        // Client assumes the server adjusts its window size accordingly
        System.out.println("Requested window size of " + this.windowSize + " sent to server.");
    }

    private String uploadFile() throws IOException {
        Packet wrqPacket = new Packet(OpCode.WRQ, this.filename);
        packetHandler.sendPacket(wrqPacket);

        String filePath = FileUtil.CLIENT_DIR + File.separator + filename;
        try (InputStream fileStream = Files.newInputStream(Paths.get(filePath))) {
            byte[] buffer = new byte[Packet.MAX_PACKET_SIZE - Packet.HEADER_SIZE];
            int bytesRead;
            while ((bytesRead = fileStream.read(buffer)) != -1) {
                byte[] dataChunk = Arrays.copyOf(buffer, bytesRead); // Copy the bytes read
                byte[] encryptedContent = EncryptionUtil.xorEncryptDecrypt(dataChunk, key);

                Packet packet = new Packet(OpCode.DATA, slidingWindow.getNextSequenceNumber(), sessionId,
                        encryptedContent);
                slidingWindow.queuePacket(packet);
                if (!dropPackets || random.nextDouble() >= 0.01) {
                    startTransferTimer();
                    packetHandler.sendPacket(packet);
                    displayThroughput(packet.getData().length);
                }
            }
        }

        // Handle retransmissions
        Queue<Packet> packetsForRetransmission = slidingWindow.getPacketsForRetransmission();
        for (Packet packet : packetsForRetransmission) {
            startTransferTimer();
            packetHandler.sendPacket(packet);
            displayThroughput(packet.getData().length);
        }

        Packet endOfTransferPacket = new Packet(OpCode.END_OF_TRANSFER, slidingWindow.getNextSequenceNumber(),
                sessionId, new byte[0]);
        packetHandler.sendPacket(endOfTransferPacket);

        System.out.println("File upload completed for: " + filename);

        return filePath;
    }

    private String downloadFile() throws IOException {
        Packet requestPacket = new Packet(OpCode.RRQ, this.filename);
        packetHandler.sendPacket(requestPacket);

        ByteArrayOutputStream downloadedContent = new ByteArrayOutputStream();
        boolean fileTransferComplete = false;

        while (!fileTransferComplete) {
            Packet receivedPacket = packetHandler.receivePacket();

            // Simulate a 1% packet drop
            if (dropPackets && random.nextDouble() < 0.01) {
                continue;
            }

            if (receivedPacket != null) {
                if (receivedPacket.getOpCode() == OpCode.DATA && receivedPacket.getData() != null) {
                    byte[] decryptedData = EncryptionUtil.xorEncryptDecrypt(receivedPacket.getData(), key);
                    downloadedContent.write(decryptedData);
                    slidingWindow.acknowledgePacket(receivedPacket.getSequenceNumber());
                    packetHandler.sendAck(receivedPacket.getSequenceNumber(), sessionId); // ACK each received DATA
                                                                                          // packet.
                    startTransferTimer();
                    displayThroughput(decryptedData.length);
                } else if (receivedPacket.getOpCode() == OpCode.END_OF_TRANSFER) {
                    slidingWindow.acknowledgePacket(receivedPacket.getSequenceNumber());
                    packetHandler.sendAck(receivedPacket.getSequenceNumber(), sessionId); // ACK the END_OF_TRANSFER
                                                                                          // packet.
                    fileTransferComplete = true; // Mark transfer as complete to exit the loop.
                }
            }
        }

        byte[] fileContent = downloadedContent.toByteArray();
        String downloadedFilePath = FileUtil.writeFile(this.filename, fileContent, false); // Ensure this method
                                                                                           // correctly writes the file.
        System.out.println("Download completed for: " + this.filename);

        return downloadedFilePath;
    }

    private void validateFile(String filePath) throws IOException, NoSuchAlgorithmException {
        String checksum = FileUtil.generateChecksum(filePath);
        System.out.println("Checksum for " + filePath + ": " + checksum);
        System.out.println("File validation complete.");
    }
}