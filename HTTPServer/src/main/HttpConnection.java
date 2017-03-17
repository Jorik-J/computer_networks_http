package main;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

public class HttpConnection implements Runnable {

	private static String PATH = "files/";
	
	private Socket socket;
	private BufferedInputStream request;
	private PrintWriter response;
	
	private String webpage;
	
	public HttpConnection(Socket socket) throws IOException {
		this.socket = socket;
		this.request = new BufferedInputStream(socket.getInputStream());
		this.response = new PrintWriter(socket.getOutputStream());
		
		//this.webpage = new String(Files.readAllBytes(Paths.get("C:/Users/bauwe_000/Desktop/files/www.google.be/index.html")));
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				
				// read the request
				
				String requestLine = null;
				while (requestLine == null) {
					requestLine = readLine(request);
				}
				
				System.out.println("rl: " + requestLine.getBytes()[0]);
				String[] parts = requestLine.replaceAll("\\s+", " ").split(" ");
				String method = parts[0];
				String path = parts[1];
				String version = parts[2];
				
				HashMap<String, String> headers = new HashMap<>();
				
				while (true) {
					String line = readLine(request);
					
					if (line.length() == 0) {
						break;
					}
					
					parseHeader(headers, line);
				}
				
				
				for (String name : headers.keySet()) {
					System.out.println(name + ": " + headers.get(name));
				}
				
				if (method.equals("POST") || method.equals("PUT")) {
					if (!headers.containsKey("content-length")) {
						break;
					}
					
					int contentLength = Integer.parseInt(headers.get("content-length"));
					//byte[] body = readCount(request, contentLength);
					String body = readLine(request);
					
					writeTextFile(method + ".txt", body);
					
					System.out.println("BODY: " + body);
				}
				
				if (!version.equals("HTTP/1.1")) {
					System.out.println("NOT HTTP/1.1! But " + version);
					response.println("HTTP/1.1 501 Not Implemented");
					response.println("");
					response.flush();
					continue;
				}
				
				
				// write the response
				
				String message = "oeps!";
				
				if (path.equals("/")) {
					message = "<h1>Zalig!</h1><br>Dit is <b>fijn</b>, niet?";
				} else {
					message = "Klik <a href='www.google.be'>hier</a>!";
				}
				
				String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
				
				response.println("HTTP/1.1 200 OK");
				response.println("Date: " + date);
				response.println("Content-Type: text/html");
				response.println("Content-Length: " + message.getBytes().length);
				response.println("");
				response.println(message);
				response.flush();
			}
			
			request.close();
			response.close();
			socket.close();
		} catch (IOException e) {
			return;
		}
	}
	
	private static void parseHeader(HashMap<String, String> headers, String line) {
		String[] parts = line.split(":");
		
		headers.put(parts[0].trim().toLowerCase(), parts[1].trim());
	}
	
	private static String readLine(BufferedInputStream bis) throws IOException {
		String line = "";
		int o = bis.read();
		
		while (o != -1) {
			char c = (char) o;
			
			// dit stond er: b == '\r' && c == '\n' (CRLF en enkel LF worden geaccepteerd)
			if (c == '\n') {
				return line.trim();
			}
			
			line += c;
			o = bis.read();
		}
		
		return null;
	}
	
	private static byte[] readCount(BufferedInputStream bis, int count) throws IOException {
		byte[] body = new byte[count];
		
		for (int i = 0; i < count; i++) {
			body[i] = (byte) bis.read();
		}
		
		return body;
	}
	
	private static void writeTextFile(String path, String content) {
		try {
			File file = new File(PATH + path);
			file.getParentFile().mkdirs();
			
			FileWriter out = new FileWriter(file);
			out.write(content);
			out.close();
			
			System.out.println("TEXT FILE CREATED: " + PATH + path);
		} catch (Exception e) {
			System.out.println("Error: couldn't save text file '" + path + "'.");
		}
	}
}
