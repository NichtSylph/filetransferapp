package filetransferappjs;

public class EncryptionUtil {
    public static byte[] generateKey(int senderId, long randomNumber) {
        String keyBase = senderId + ":" + randomNumber;
        return keyBase.getBytes();
    }

    public static byte[] xorEncryptDecrypt(byte[] data, byte[] key) {
        byte[] result = new byte[data.length];

        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }

        return result;
    }
}
