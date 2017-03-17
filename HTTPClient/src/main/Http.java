package main;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;

public class Http {
	
	public static HttpResponse HEAD(URI uri, int port) {
		return sendRequest("HEAD", uri, port, null);
	}
	
	public static HttpResponse GET(URI uri, int port) {
		return sendRequest("GET", uri, port, null);
	}
	
	public static HttpResponse POST(URI uri, int port, String message) {
		return sendRequest("POST", uri, port, message);
	}
	
	public static HttpResponse PUT(URI uri, int port, String message) {
		return sendRequest("PUT", uri, port, message);
	}
	
	private static HttpResponse sendRequest(String method, URI uri, int port, String message) {
		System.out.println("REQUEST VERZONDEN");
		String host = uri.getHost();
		String path = uri.getPath();
		int embeddedPort = uri.getPort();
		
		if (embeddedPort >= 0) {
			port = embeddedPort;
		}
		
		try {
			Socket socket = new Socket(host, port);
			
			
			// write the request
			
			PrintWriter request = new PrintWriter(socket.getOutputStream());
			
			request.println(method + " " + path + " HTTP/1.0");
			request.println("Host: " + host + ":" + port);
			
			if (message != null) {
				request.println("Content-Type: text/plain; charset=utf-8");
				request.println("Content-Length: " + message.getBytes().length);
			}
			
			request.println("");
			
			if (message != null) {
				request.println(message);
			}
			
			request.flush();
			
			
			// read the response
			
			BufferedInputStream response = new BufferedInputStream(socket.getInputStream());
			
			String statusLine = readLine(response);
			HashMap<String, String> headers = new HashMap<>();
			
			while (true) {
				String line = readLine(response);
				
				if (line.length() == 0) {
					break;
				}
				
				parseHeader(headers, line);
			}
			
			int statusCode = Integer.parseInt(statusLine.replaceAll("\\s+", " ").split(" ")[1]);
			
			byte[] body = null;
			
			if (!method.equals("HEAD") && statusCode != 501) {
				if (headers.containsKey("content-length")) {
					int contentLength = Integer.parseInt(headers.get("content-length")); 
					body = readCount(response, contentLength);
				}
				else if (headers.containsKey("transfer-encoding") && headers.get("transfer-encoding").equals("chunked")) {
					ArrayList<byte[]> chunks = new ArrayList<>();
					int totalSize = 0;
					
					while (true) {
						byte[] chunk = readChunk(response);
						
						if (chunk.length == 0) {
							break;
						}
						
						chunks.add(chunk);
						totalSize += chunk.length;
					}
					
					body = new byte[totalSize];
					int i = 0;
					
					for (byte[] chunk : chunks) {
						for (byte b : chunk) {
							body[i++] = b;
						}
					}
				}
				else {
					response.close();
					request.close();
					socket.close();
					
					return null;
				}
			}
			
			response.close();
			request.close();
			socket.close();
			
			return new HttpResponse(method, uri, statusLine, headers, body);
		} catch (IOException e) {
			e.printStackTrace();
			
			return null;
		}
	}
	
	private static void parseHeader(HashMap<String, String> headers, String line) {
		int index = line.indexOf(":");
		String name = line.substring(0, index).trim().toLowerCase();
		String value = line.substring(index + 1).trim();
		
		headers.put(name, value);
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
	
	private static byte[] readChunk(BufferedInputStream bis) throws IOException {
		String t = readLine(bis);
		
		int chunkLength = Integer.parseInt(t, 16);  // TODO: kan ";" ofzoiets nog achter komen!
		byte[] chunk = new byte[chunkLength];
		
		for (int i = 0; i < chunkLength; i++) {
			chunk[i] = (byte) bis.read();
		}
		
	    char c = (char) bis.read();
		if (c != '\n') {
			bis.read();
		}
		
		return chunk;
	}
}
