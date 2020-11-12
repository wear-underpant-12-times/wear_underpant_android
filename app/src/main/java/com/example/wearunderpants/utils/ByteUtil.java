package com.example.wearunderpants.utils;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ByteUtil {
    public static byte[] intTo8bytes(int data) {
        return Arrays.copyOfRange(ByteBuffer.allocate(4).putInt(data).array(), 2, 4);
    }
    public static byte[] makeIpChecksum(byte[] ipHeader) {
        ipHeader[10] = 0;
        ipHeader[11] = 0;
        return intTo8bytes(calculateChecksum(ipHeader));
    }
    public static byte[] makeUdpChecksum(byte[] ipHeader, byte[] udpHeader, byte[] udpData) {
        udpHeader[6] = 0;
        udpHeader[7] = 0;
        byte[] data = new byte[udpData.length + 20];
        System.arraycopy(ipHeader, 12, data, 0, 8);
        data[8] = 0;
        data[9] = 17;
        System.arraycopy(udpHeader, 4, data, 10, 2);
        System.arraycopy(udpHeader, 0, data, 12, 8);
        System.arraycopy(udpData, 0, data, 20, udpData.length);
        return intTo8bytes(calculateChecksum(data));
    }
    public static int calculateChecksum(byte[] buf) {
        int length = buf.length;
        int i = 0;
        int sum = 0;
        long data;
        // Handle all pairs
        while (length > 1) {
            // Corrected to include @Andy's edits and various comments on Stack Overflow
            data = (((buf[i] << 8) & 0xFF00) | ((buf[i + 1]) & 0xFF));
            sum += data;
            // 1's complement carry bit correction in 16-bits (detecting sign extension)
            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0xFFFF;
                sum += 1;
            }
            i += 2;
            length -= 2;
        }
        // Handle remaining byte in odd length buffers
        if (length > 0) {
            // Corrected to include @Andy's edits and various comments on Stack Overflow
            sum += (buf[i] << 8 & 0xFF00);
            // 1's complement carry bit correction in 16-bits (detecting sign extension)
            if ((sum & 0xFFFF0000) > 0) {
                sum = sum & 0xFFFF;
                sum += 1;
            }
        }
        // Final 1's complement value correction to 16-bits
        sum = ~sum;
        sum = sum & 0xFFFF;
        return sum;
    }
}
