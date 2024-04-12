package filetransferappjs;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;

public class SlidingWindow {
    private int windowSize;
    private int base = 0; // The sequence number of the oldest unacknowledged packet
    private int nextSeqNum = 0; // The sequence number for the next packet to be sent
    private final Queue<Packet> packetQueue = new LinkedList<>();
    private final long retransmissionTimeoutMs = 2000; // Retransmission timeout in milliseconds

    public SlidingWindow(int windowSize) {
        this.windowSize = windowSize;
    }

    public synchronized void queuePacket(Packet packet) {
        if (canSendNewPacket()) {
            packet.setSequenceNumber(nextSeqNum);
            packet.markAsSent();
            packetQueue.add(packet);
            nextSeqNum = (nextSeqNum + 1) % Integer.MAX_VALUE; // Handle sequence number wrapping if necessary
        }
    }

    public synchronized void acknowledgePacket(int ackSeqNum) {
        while (!packetQueue.isEmpty() && packetQueue.peek().getSequenceNumber() <= ackSeqNum) {
            packetQueue.poll();
            base = (base + 1) % Integer.MAX_VALUE; // Handle sequence number wrapping if necessary
        }
    }

    public synchronized Queue<Packet> getPacketsForRetransmission() {
        Queue<Packet> packetsForRetransmission = new LinkedList<>();
        for (Packet packet : packetQueue) {
            if (Instant.now().minusMillis(packet.getLastSent().toEpochMilli()).toEpochMilli() > retransmissionTimeoutMs) {
                packet.markAsSent(); // Update the last sent time before retransmission
                packetsForRetransmission.add(packet);
            }
        }
        return packetsForRetransmission;
    }

    public boolean canSendNewPacket() {
        return packetQueue.size() < windowSize;
    }

    public synchronized int getNextSequenceNumber() {
        int currentSeqNum = this.nextSeqNum;
        this.nextSeqNum++;  // Simply increment the next sequence number
        return currentSeqNum;
    }
    

    public boolean isWindowFull() {
        return packetQueue.size() >= windowSize;
    }

    public void setWindowSize(int clientWindowSize) {
        this.windowSize = clientWindowSize;
    }

    public String getWindowSize() {
        return Integer.toString(windowSize);
    }
}
