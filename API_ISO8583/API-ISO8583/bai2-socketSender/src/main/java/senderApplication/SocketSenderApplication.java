package senderApplication;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOException;
import org.jpos.iso.packager.ISO87APackager;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Scanner;

//1) Cho nhập sourcePan, destPan cho Inquiry -> no amount
//2) Cho nhập amount (>=1000) cho Payment
//3) Payment chỉ được thực hiện nếu confirmCode != null

public class SocketSenderApplication {

	private static String lastConfirmCode = "";

	public static void main(String[] args) {
		SocketSenderApplication app = new SocketSenderApplication();
		app.runMenu();
	}

	private void runMenu() {
		Scanner sc = new Scanner(System.in);
		while (true) {
			System.out.println("\n=== MENU ===");
			System.out.println("1. Gửi Inquiry (432020)");
			System.out.println("2. Gửi Payment (912020)");
			System.out.println("0. Thoát");
			System.out.print("Chọn: ");
			String choice = sc.nextLine().trim();

			if ("1".equals(choice)) {
				doInquiry();
			} else if ("2".equals(choice)) {
				if (lastConfirmCode == null || lastConfirmCode.isEmpty()) {
					System.out.println("[Sender] Lỗi: Chưa có confirmCode (chưa Inquiry)!");
					continue;
				}
				doPayment();
			} else if ("0".equals(choice)) {
				System.out.println("Kết thúc.");
				break;
			} else {
				System.out.println("Lựa chọn không hợp lệ!");
			}
		}
	}

	private void doInquiry() {
		Scanner sc = new Scanner(System.in);

		System.out.print("Nhập sourcePan: ");
		String sourcePan = sc.nextLine().trim();

		System.out.print("Nhập destPan: ");
		String destPan = sc.nextLine().trim();
		for (int i = 0; i < 100; i++) {
			try {
				System.out.println("lan :" + i);
				ISOMsg isoReq = new ISOMsg();
				isoReq.setPackager(new ISO87APackager());
				isoReq.setMTI("0200");
				isoReq.set(2, sourcePan);
				isoReq.set(3, "432020");
				isoReq.set(4, "");
				isoReq.set(47, destPan);
				isoReq.set(48, "");

				System.out.println("[Sender] Gửi INQUIRY...");
				debugIso(isoReq);

				byte[] response = sendToSocketServer(isoReq.pack());
				if (response == null) {
					System.err.println("[Sender] Không nhận được response!");
					return;
				}

				ISOMsg isoResp = new ISOMsg();
				isoResp.setPackager(new ISO87APackager());
				isoResp.unpack(response);

				System.out.println("[Sender] INQUIRY RESP:");
				debugIso(isoResp);

				if (isoResp.hasField(48)) {
					lastConfirmCode = isoResp.getString(48);
					System.out.println(">>> confirmCode = " + lastConfirmCode);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private void doPayment() {
		Scanner sc = new Scanner(System.in);

		System.out.print("Nhập sourcePan (phải trùng inquiry): ");
		String sourcePan = sc.nextLine().trim();

		System.out.print("Nhập destPan (phải trùng inquiry): ");
		String destPan = sc.nextLine().trim();

		System.out.print("Nhập amount (>=1000): ");
		String amount = sc.nextLine().trim();

		try {
			ISOMsg isoReq = new ISOMsg();
			isoReq.setPackager(new ISO87APackager());
			isoReq.setMTI("0200");
			isoReq.set(2, sourcePan);
			isoReq.set(3, "912020");
			isoReq.set(4, amount); // >= 1000
			isoReq.set(47, destPan);
			isoReq.set(48, lastConfirmCode);

			System.out.println("[Sender] Gửi PAYMENT...");
			debugIso(isoReq);

			byte[] response = sendToSocketServer(isoReq.pack());
			if (response == null) {
				System.err.println("[Sender] Không nhận được response!");
				return;
			}

			ISOMsg isoResp = new ISOMsg();
			isoResp.setPackager(new ISO87APackager());
			isoResp.unpack(response);

			System.out.println("[Sender] PAYMENT RESP:");
			debugIso(isoResp);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private byte[] sendToSocketServer(byte[] data) {
		try (Socket socket = new Socket("localhost", 1234);
				OutputStream out = socket.getOutputStream();
				InputStream in = socket.getInputStream()) {

			out.write(data);
			out.flush();
			byte[] buf = new byte[4096];
			int len = in.read(buf);
			if (len > 0) {
				byte[] resp = new byte[len];
				System.arraycopy(buf, 0, resp, 0, len);
				return resp;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private void debugIso(ISOMsg iso) throws ISOException {
		System.out.println("  MTI=" + iso.getMTI());
		for (int i = 1; i <= iso.getMaxField(); i++) {
			if (iso.hasField(i)) {
				System.out.println("  Field " + i + "=" + iso.getString(i));
			}
		}
	}
}
