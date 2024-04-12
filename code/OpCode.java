package filetransferappjs;

// Enum defining TFTP operation codes with extension for option acknowledgment (OACK)
public enum OpCode {
    RRQ(1),      // Read request
    WRQ(2),      // Write request
    DATA(3),     // Data
    ACK(4),      // Acknowledgment
    ERROR(5),    // Error
    OACK(6),     // Option Acknowledgment, only used for windowsize option
    SESSION_START(7), // Session start (custom extension for session initiation)
    END_OF_TRANSFER(8); // End of transfer (custom extension for session termination)

    private final int value;

    OpCode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static OpCode fromInt(int value) {
        for (OpCode op : values()) {
            if (op.getValue() == value) {
                return op;
            }
        }
        throw new IllegalArgumentException("Invalid OpCode value: " + value);
    }
}