package utils;

import org.jpos.iso.ISOBasePackager;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class IsoSocketUtils {

    public static void sendIsoMessageWithHeader(OutputStream out, ISOMsg iso) throws ISOException, IOException {
        byte[] packed = iso.pack();
        byte[] dataWithHeader = addLengthHeader(packed);
        System.out.println("[IsoSocketUtils] Sending message with header: " 
                           + new String(dataWithHeader, 0, 4, StandardCharsets.UTF_8)
                           + ", length=" + packed.length);
        out.write(dataWithHeader);
        out.flush();
    }

    public static ISOMsg readIsoMessageWithHeader(InputStream in, ISOBasePackager packager) throws IOException, ISOException {
        byte[] msgData = readMessageWithHeader(in);
        if (msgData == null) {
            throw new IOException("Không nhận được dữ liệu đầy đủ từ socket.");
        }
        ISOMsg iso = new ISOMsg();
        iso.setPackager(packager);
        iso.unpack(msgData);
        if (iso.getMTI() == null || iso.getMTI().trim().isEmpty()) {
            throw new ISOException("MTI is missing or invalid.");
        }
        return iso;
    }

    public static byte[] addLengthHeader(byte[] data) {
        int length = data.length;
        String headerStr = String.format("%04d", length);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(headerStr.getBytes(StandardCharsets.UTF_8), 0, 4);
        baos.write(data, 0, data.length);
        return baos.toByteArray();
    }

    public static byte[] readMessageWithHeader(InputStream in) throws IOException {
        byte[] header = new byte[4];
        int bytesRead = 0;
        long startTime = System.currentTimeMillis();
        long maxWaitMillis = 10000; // Chờ đến 10 giây
        while (bytesRead < 4 && (System.currentTimeMillis() - startTime) < maxWaitMillis) {
            int r = in.read(header, bytesRead, 4 - bytesRead);
            if (r == -1) break;
            bytesRead += r;
        }
        if (bytesRead < 4) {
            throw new IOException("Không thể đọc đủ header 4-byte sau " + maxWaitMillis + " ms, bytesRead=" + bytesRead);
        }
        String headerStr = new String(header, StandardCharsets.UTF_8);
        int msgLength;
        try {
            msgLength = Integer.parseInt(headerStr);
        } catch (NumberFormatException e) {
            throw new IOException("Header không hợp lệ: " + headerStr);
        }
        System.out.println("[IsoSocketUtils] Header read: " + headerStr + ", expecting message length=" + msgLength);
        byte[] data = new byte[msgLength];
        int offset = 0;
        while (offset < msgLength) {
            int r = in.read(data, offset, msgLength - offset);
            if (r == -1) {
                throw new IOException("Không nhận đủ dữ liệu. Mong đợi=" + msgLength + " byte, nhận được=" + offset);
            }
            offset += r;
        }
        return data;
    }
}
