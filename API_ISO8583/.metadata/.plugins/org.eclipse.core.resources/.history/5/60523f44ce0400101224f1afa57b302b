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
        System.out.println("[IsoSocketUtils] Sending message with header: " + new String(dataWithHeader, 0, 4, StandardCharsets.UTF_8) + ", length=" + packed.length);
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

        // Cố gắng đọc đủ 4 byte header
        while (bytesRead < 4) {
            int r = in.read(header, bytesRead, 4 - bytesRead);
            if (r == -1) {
                System.err.println("[IsoSocketUtils] Failed to read header: reached end of stream, bytesRead=" + bytesRead);
                throw new IOException("Không thể đọc đủ header 4-byte.");
            }
            bytesRead += r;
            System.out.println("[IsoSocketUtils] Read " + bytesRead + " bytes of header: " + new String(header, 0, bytesRead, StandardCharsets.UTF_8));
        }

        // Chuyển header thành chuỗi và lấy độ dài message
        String headerStr = new String(header, StandardCharsets.UTF_8);
        int msgLength;
        try {
            msgLength = Integer.parseInt(headerStr);
        } catch (NumberFormatException e) {
            throw new IOException("Header không hợp lệ: " + headerStr);
        }
        System.out.println("[IsoSocketUtils] Header read: " + headerStr + ", expecting message length=" + msgLength);

        // Đọc phần dữ liệu còn lại của message
        byte[] data = new byte[msgLength];
        int offset = 0;
        long startTime = System.currentTimeMillis();
        long maxWaitMillis = 5000; // Tăng thời gian chờ lên 5 giây

        while (offset < msgLength) {
            int r = in.read(data, offset, msgLength - offset);
            if (r == -1) {
                if (System.currentTimeMillis() - startTime < maxWaitMillis) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Đã bị gián đoạn trong khi chờ dữ liệu", ie);
                    }
                    continue;
                } else {
                    throw new IOException("Không nhận đủ dữ liệu. Mong đợi=" + msgLength + " byte, nhận được=" + offset);
                }
            }
            offset += r;
            System.out.println("[IsoSocketUtils] Read " + offset + " of " + msgLength + " bytes of message data");
        }
        return data;
    }
}