package main;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class HttpConnection {
	
	private String host;
	private int port;
	
	private Socket socket;
	private PrintWriter request;
	private BufferedInputStream response;
	
	public HttpConnection(String host, int port) throws IOException {
		this.host = host;
		this.port = port;
		
		this.socket = new Socket(host, port);
		this.request = new PrintWriter(socket.getOutputStream());
		this.response = new BufferedInputStream(socket.getInputStream());
	}
	
	public void close() throws IOException {
		response.close();
		request.close();
		socket.close();
	}
	
	public HttpResponse HEAD(String path) {
		return sendRequest("HEAD", path, null);
	}
	
	public HttpResponse GET(String path) {
		return sendRequest("GET", path, null);
	}
	
	public HttpResponse POST(String path, String message) {
		return sendRequest("POST", path, message);
	}
	
	public HttpResponse PUT(String path, String message) {
		return sendRequest("PUT", path, message);
	}
	
	private HttpResponse sendRequest(String method, String path, String message) {
		path = path.replaceAll(" ", "%20");
		
		writeRequest(method, path, message);
		return readResponse(method);
	}
	
	private void writeRequest(String method, String path, String message) {
		request.println(method + " " + path + " HTTP/1.1");
		request.println("Host: " + host + ":" + port);
		
		if (message != null) {
			request.println("Content-Type: text/plain; charset=utf-8");
			request.println("Content-Length: " + message.getBytes().length);
		}
		
		request.println("");
		
		if (message != null) {
			request.print(message);
		}
		
		request.flush();
	}
	
	private HttpResponse readResponse(String method) {
		try {
			String statusLine = readLine();
			HashMap<String, String> headers = new HashMap<>();
			
			while (true) {
				String line = readLine();
				
				if (line.length() == 0) {
					break;
				}
				
				parseHeader(headers, line);
			}
			
			int statusCode = Integer.parseInt(statusLine.replaceAll("\\s+", " ").split(" ")[1]);
			
			byte[] body = null;
			
			if (!method.equals("HEAD") && 200 <= statusCode && statusCode < 400) {
				if (headers.containsKey("content-length")) {
					int contentLength = Integer.parseInt(headers.get("content-length")); 
					body = readCount(contentLength);
				}
				else if (headers.containsKey("transfer-encoding") && headers.get("transfer-encoding").equals("chunked")) {
					body = readChunks();
				}
				else {
					return null;
				}
			}
			
			if (statusCode == 302) {
				// TODO: redirect
				return readResponse("GET");
			}
			
			return new HttpResponse(method, statusLine, headers, body);
		} catch (IOException e) {
			return null;
		}
	}

	private void parseHeader(HashMap<String, String> headers, String line) {
		int index = line.indexOf(":");		
		String name = line.substring(0, index).trim().toLowerCase();
		String value = line.substring(index + 1).trim();
		
		headers.put(name, value);
	}
	
	private String readLine() throws IOException {
		String line = "";
		
		while (true) {
			int o = response.read();
			
			if (o == -1) {
				break;
			}
			
			char c = (char) o;
			
			if (c == '\n') {
				return line.trim();
			}
			
			if (c != '\r') {
				line += c;
			}
		}
		
		return null;
	}
	
	private byte[] readCount(int count) throws IOException {
		byte[] body = new byte[count];
		
		for (int i = 0; i < count; i++) {
			body[i] = (byte) response.read();
		}
		
		return body;
	}
	
	private byte[] readChunks() throws IOException {
		ArrayList<byte[]> chunks = new ArrayList<>();
		int totalSize = 0;
		
		while (true) {
			byte[] chunk = readChunk();
			
			if (chunk.length == 0) {
				break;
			}
			
			chunks.add(chunk);
			totalSize += chunk.length;
		}
		
		byte[] body = new byte[totalSize];
		int i = 0;
		
		for (byte[] chunk : chunks) {
			for (byte b : chunk) {
				body[i++] = b;
			}
		}
		
		return body;
	}
	
	private byte[] readChunk() throws IOException {
		String t = readLine();
		
		int chunkLength = Integer.parseInt(t.split(";")[0], 16);
		byte[] chunk = new byte[chunkLength];
		
		for (int i = 0; i < chunkLength; i++) {
			chunk[i] = (byte) response.read();
		}
		
	    char c = (char) response.read();
		if (c != '\n') {
			response.read();
		}
		
		return chunk;
	}
}
