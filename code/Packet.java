package filetransferappjs;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Packet {
    public static final int MAX_PACKET_SIZE = 1024;
    public static final int HEADER_SIZE = 80;
    private OpCode opCode;
    private int sequenceNumber;
    private int sessionId;
    private String fileName;
    private byte[] data;
    private Map<String, String> options;
    private Instant lastSent; // Timestamp of when the packet was last sent

    // Constructors
    public Packet(OpCode opCode, int sequenceNumber, int sessionId, byte[] data) {
        this.opCode = opCode;
        this.sequenceNumber = sequenceNumber;
        this.sessionId = sessionId;
        this.data = data;
        this.options = new HashMap<>();
        this.lastSent = null; // Initialize to null indicating not yet sent
    }

    public Packet(OpCode opCode, String fileName) {
        this.opCode = opCode;
        this.fileName = fileName;
        this.options = new HashMap<>();
        this.lastSent = null;
    }

    public Packet(OpCode opCode, Map<String, String> options) {
        this.opCode = opCode;
        this.options = options;
        this.lastSent = null;
    }

    public OpCode getOpCode() {
        return opCode;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public int getSessionId() {
        return sessionId;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public Map<String, String> getOptions() {
        return options;
    }

    public void markAsSent() {
        this.lastSent = Instant.now();
    }

    public void updateLastSent(Instant lastSent) {
        this.lastSent = lastSent;
    }

    public Instant getLastSent() {
        return lastSent;
    }

    public void setSequenceNumber(int nextSeqNum) {
        this.sequenceNumber = nextSeqNum;
    }

    // Serialization method
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
        buffer.put((byte) opCode.ordinal());
        buffer.putInt(sequenceNumber);
        buffer.putInt(sessionId);
    
        // Placeholder for the actual size fields (to be filled later)
        int fileNameSizePosition = buffer.position();
        buffer.putInt(0); // Placeholder for fileName length
        if (fileName != null) {
            byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            if (buffer.position() + fileNameBytes.length > MAX_PACKET_SIZE) {
                throw new RuntimeException("Packet size exceeds the maximum limit.");
            }
            buffer.put(fileNameBytes);
            buffer.putInt(fileNameSizePosition, fileNameBytes.length);
        }
    
        int dataSizePosition = buffer.position();
        buffer.putInt(0); // Placeholder for data length
        if (data != null) {
            if (buffer.position() + data.length > MAX_PACKET_SIZE) {
                throw new RuntimeException("Packet size exceeds the maximum limit.");
            }
            buffer.put(data);
            buffer.putInt(dataSizePosition, data.length);
        }
    
        buffer.putInt(options.size());
        options.forEach((key, value) -> {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(keyBytes.length);
            buffer.put(keyBytes);
            buffer.putInt(valueBytes.length);
            buffer.put(valueBytes);
        });
    
        // Handling lastSent similarly to other fields
        long lastSentEpochMilli = lastSent != null ? lastSent.toEpochMilli() : -1;
        buffer.putLong(lastSentEpochMilli);
    
        buffer.flip();
        byte[] packetBytes = new byte[buffer.limit()];
        buffer.get(packetBytes);
        return packetBytes;
    }

    // Deserialization method
    public static Packet fromByteBuffer(ByteBuffer buffer) {
        OpCode opCode = OpCode.values()[buffer.get() & 0xFF];
        int sequenceNumber = buffer.getInt();
        int sessionId = buffer.getInt();

        String fileName = null;
        byte[] data = null;
        Map<String, String> options = new HashMap<>();

        // Filename
        int fileNameLength = buffer.getInt();
        if (fileNameLength > 0) {
            byte[] fileNameBytes = new byte[fileNameLength];
            buffer.get(fileNameBytes);
            fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
        }

        // Data
        int dataLength = buffer.getInt();
        if (dataLength > 0) {
            data = new byte[dataLength];
            buffer.get(data);
        }

        // Options
        int optionsSize = buffer.getInt();
        for (int i = 0; i < optionsSize; i++) {
            int keyLength = buffer.getInt();
            byte[] keyBytes = new byte[keyLength];
            buffer.get(keyBytes);
            String key = new String(keyBytes, StandardCharsets.UTF_8);

            int valueLength = buffer.getInt();
            byte[] valueBytes = new byte[valueLength];
            buffer.get(valueBytes);
            String value = new String(valueBytes, StandardCharsets.UTF_8);

            options.put(key, value);
        }

        // LastSent
        long lastSentEpochMilli = buffer.getLong();
        Instant lastSent = lastSentEpochMilli != -1 ? Instant.ofEpochMilli(lastSentEpochMilli) : null;

        Packet packet = new Packet(opCode, sequenceNumber, sessionId, data); // Modify as needed for other constructors
        packet.fileName = fileName;
        packet.options = options;
        packet.lastSent = lastSent;
        return packet;
    }
}