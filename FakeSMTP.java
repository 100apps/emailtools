import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class FakeSMTP {
	static Logger log = Logger.getLogger("FakeSMPT");
	String workDir = "/tmp/";

	private void write(OutputStream out, String line)
			throws UnsupportedEncodingException, IOException {
		System.out.println("<-- " + line);
		out.write((line + "\r\n").getBytes("UTF-8"));
	}

	private String writeAndRead(OutputStream out, String line, Scanner scan)
			throws UnsupportedEncodingException, IOException {
		write(out, line);
		String ret = scan.nextLine();
		System.out.println("--> " + ret);
		return ret;
	}

	private boolean writeAndCheck(OutputStream out, String line, Scanner scan,
			String... prefix) throws UnsupportedEncodingException, IOException {
		String ret = writeAndRead(out, line, scan);
		for (String s : prefix) {
			if (ret.startsWith(s))
				return true;
		}
		return false;
	}

	private String getAddr(String line) {
		return line.substring(line.indexOf('<') + 1, line.indexOf('>')).trim();
	}

	private void handle(Socket s) {
		try {
			OutputStream out = s.getOutputStream();
			Scanner scan = new Scanner(s.getInputStream());
			if (writeAndCheck(out, "220 ESMTP Postfix", scan, "HELO", "EHLO")) {
				String from = getAddr(writeAndRead(out, "250 Hello", scan));
				String to = getAddr(writeAndRead(out, "250 Ok", scan));
				if (writeAndCheck(out, "250 Ok", scan, "DATA")) {
					write(out, "354 End data with <CR><LF>.<CR><LF>");
					StringBuilder data = new StringBuilder();
					while (scan.hasNextLine()) {
						String line = scan.nextLine();
						if (line.trim().equals("."))
							break;
						data.append(line).append("\r\n");
					}
					long ctime = System.currentTimeMillis();
					log.info(from + "-->" + to + "\t" + data.length());
					// 这里可以处理收到的邮件。
					Files.write(Paths.get(workDir + from + "-" + to + ctime
							+ ".eml"), data.toString().getBytes("UTF-8"));

					if (writeAndCheck(out, "250 Ok", scan, "quit"))
						write(out, "221 Bye");
				}
			}
			scan.close();
			out.close();
			s.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static void main(String[] args) {
		try (ServerSocket ss = new ServerSocket(1025)) {
			int count = 0;
			log.info("server started");
			FakeSMTP smtp = new FakeSMTP();
			ExecutorService ser = Executors.newFixedThreadPool(10);

			while (true) {
				Socket s = ss.accept();
				log.info((count++) + "\t" + s.getRemoteSocketAddress()
						+ " connected");
				ser.execute(new Runnable() {
					@Override
					public void run() {
						smtp.handle(s);
					}
				});
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
