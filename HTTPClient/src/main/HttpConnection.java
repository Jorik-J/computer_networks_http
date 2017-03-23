package main;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Represents an HTTP/1.1 persistent client connection.
 * 
 * @author Bauwen Demol (r0583318)
 * @author Jorik Jooken (r0588270)
 */
public class HttpConnection {
	
	private String host;
	private int port;
	
	private Socket socket;
	private PrintWriter request;
	private BufferedInputStream response;
	
	/**
	 * Constructs an HttpConnection from the given components.
	 * 
	 * @param host
	 * 		The host to connect to (e.g. "www.example.com")
	 * @param port
	 * 		The port number to connect to
	 * 
	 * @throws IOException
	 */
	public HttpConnection(String host, int port) throws IOException {
		this.host = host;
		this.port = port;
		
		this.socket = new Socket(host, port);
		this.request = new PrintWriter(socket.getOutputStream());
		this.response = new BufferedInputStream(socket.getInputStream());
	}
	
	/**
	 * Closes the HTTP connection.
	 * After this call, the connection cannot be used anymore.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		response.close();
		request.close();
		socket.close();
	}
	
	/**
	 * Sends a HEAD request to the given path
	 * 
	 * @param path
	 * 		The path to send the request to
	 * 
	 * @return
	 * 		An {@link HttpResponse}
	 */
	public HttpResponse HEAD(String path) {
		return sendRequest("HEAD", path, null);
	}
	
	/**
	 * Sends a GET request to the given path
	 * 
	 * @param path
	 * 		The path to send the request to
	 * 
	 * @return
	 * 		An {@link HttpResponse}
	 */
	public HttpResponse GET(String path) {
		return sendRequest("GET", path, null);
	}
	
	/**
	 * Sends a POST request to the given path with the given message
	 * 
	 * @param path
	 * 		The path to send the request to
	 * @param message
	 * 		The message to send with the request
	 * 
	 * @return
	 * 		An {@link HttpResponse}
	 */
	public HttpResponse POST(String path, String message) {
		return sendRequest("POST", path, message);
	}
	
	/**
	 * Sends a PUT request to the given path with the given message
	 * 
	 * @param path
	 * 		The path to send the request to
	 * @param message
	 * 		The message to send with the request
	 * 
	 * @return
	 * 		An {@link HttpResponse}
	 */
	public HttpResponse PUT(String path, String message) {
		return sendRequest("PUT", path, message);
	}
	
	/**
	 * Sends an HTTP request with the given method to the given path containing the given message.
	 * 
	 * @param method
	 * 		The request method to use (can be "HEAD", "GET", "POST" or "PUT")
	 * @param path
	 * 		The path to send the request to
	 * @param message
	 * 		The message to send with the request. May be null, indicating no body is present
	 * 
	 * @return
	 * 		An {@link HttpResponse}
	 */
	private HttpResponse sendRequest(String method, String path, String message) {
		path = path.replaceAll(" ", "%20");
		
		writeRequest(method, path, message);
		return readResponse(method);
	}
	
	/**
	 * Writes the request to the connection's output stream.
	 * 
	 * @param method
	 * 		The request method to use
	 * @param path
	 * 		The path to send the request to
	 * @param message
	 * 		The message to send with the request
	 */
	private void writeRequest(String method, String path, String message) {
		request.print(method + " " + path + " HTTP/1.1\r\n");
		request.print("Host: " + host + ":" + port + "\r\n");
		
		if (message != null) {
			request.print("Content-Type: text/plain; charset=utf-8\r\n");
			request.print("Content-Length: " + message.getBytes().length + "\r\n");
		}
		
		request.print("\r\n");
		
		if (message != null) {
			request.print(message);
		}
		
		request.flush();
	}
	
	/**
	 * Reads the response from the connection's input stream
	 * 
	 * @param method
	 * 		The method the response was requested by
	 * 
	 * @return
	 * 		An {@link HttpResponse} or null if something went wrong
	 */
	private HttpResponse readResponse(String method) {
		try {
			
			// read the status line and the headers
			
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
			
			
			// read the body if present and possible to extract
			
			byte[] body = null;
			
			if (!method.equals("HEAD")) {
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
			
			
			// redirect if necessary
			
			if (statusCode == 301 || statusCode == 302 && headers.containsKey("location")) {
			    String location = headers.get("location");
			    
			    try {
			        URI uri = new URI(location);
			        
			        String host = uri.getHost();
			        int port = uri.getPort();
			        String path = uri.getPath();
			        
			        HttpConnection connection = new HttpConnection(host, port < 0 ? 80 : port);

			        switch (method) {
			        case "HEAD":
			            System.out.println("Redirected to " + location);
			            return connection.HEAD(path);
			            
			        case "GET":
			            System.out.println("Redirected to " + location);
			            return connection.GET(path);
			        }
			    } catch (URISyntaxException e) {}
			}
			
			
			// construct and return the response
			
			return new HttpResponse(method, statusLine, headers, body);
		} catch (IOException e) {
			return null;
		}
	}
	
	/**
	 * Parses the given line and puts the field and value of the header into the given map.
	 * It links the field name (key) of the header to the value (value) of the header.
	 * 
	 * @param headers
	 * 		A map to put the parsed headers in
	 * @param line
	 * 		A line that represents an unparsed header
	 */
	private void parseHeader(HashMap<String, String> headers, String line) {
		int index = line.indexOf(":");
		
		if (index < 0) {
			return;
		}
		
		String name = line.substring(0, index).trim().toLowerCase();
		String value = line.substring(index + 1).trim();
		
		headers.put(name, value);
	}
	
	/**
	 * Reads a line from the connection's input stream.
	 * 
	 * @return
	 * 		The line that has been read or null if the input stream is closed
	 * 
	 * @throws IOException
	 */
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
	
	/**
	 * Reads the given number of bytes from the connection's input stream.
	 * Used when "Content-Length" header is present
	 * 
	 * @param count
	 * 		The number of bytes to read
	 * 
	 * @return
	 * 		An array of bytes that has been read
	 * 
	 * @throws IOException
	 */
	private byte[] readCount(int count) throws IOException {
		byte[] body = new byte[count];
		
		for (int i = 0; i < count; i++) {
			body[i] = (byte) response.read();
		}
		
		return body;
	}
	
	/**
	 * Reads a number of chunks from the connection's input stream.
	 * Used when "Transfer-Encoding: chunked" header is present.
	 * 
	 * @return
	 * 		An array of bytes composing all the chunks
	 * 
	 * @throws IOException
	 */
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
	
	/**
	 * Reads a chunk from the connection's input stream.
	 * 
	 * @return
	 * 		An array of bytes composing the chunk
	 * 
	 * @throws IOException
	 */
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
