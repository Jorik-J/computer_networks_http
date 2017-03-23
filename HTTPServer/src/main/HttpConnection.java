package main;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Represents an HTTP/1.1 persistent server connection.
 * 
 * @author Bauwen Demol (r0583318)
 * @author Jorik Jooken (r0588270)
 */
public class HttpConnection implements Runnable {

	private final String PATH;
	
	private Socket socket;
	private BufferedInputStream request;
	private BufferedOutputStream response;
	
	/**
	 * Constructs an HttpConnection from the given components.
	 * 
	 * @param socket
	 * 		The socket the connection will use
	 * @param path
	 * 		The path containing the relative location of the local web server
	 * 
	 * @throws IOException
	 */
	public HttpConnection(Socket socket, String path) throws IOException {
		this.socket = socket;
		this.request = new BufferedInputStream(socket.getInputStream());
		this.response = new BufferedOutputStream(socket.getOutputStream());
		
		PATH = "files/" + path;
	}
	
	/**
	 * Runs the thread handling the connection with a client.
	 */
	@Override
	public void run() {
		try {
			while (true) {
				
				// read the request
				
				HttpRequest request = readRequest();
				
				if (request == null) {
					break;
				}
				
				
				// print the request
				
				System.out.println(request.getRequestLine());
				
				HashMap<String, String> headers = request.getHeaders();
				
				for (String name : headers.keySet()) {
					System.out.println(name + ": " + headers.get(name));
				}
				
				System.out.println("");
				
				if (request.hasBody()) {
					System.out.println(request.getBody());
				}
				
				
				// write the appropriate response
				
				boolean closed = writeResponse(request);
				
				if (closed) {
					break;
				}
			}
		} catch (IOException e) {}
		
		close();
	}
	
	/**
	 * Reads the request from the connection's input stream.
	 * 
	 * @return
	 * 		An {@link HttpRequest} or null if the input stream is closed
	 * 
	 * @throws IOException
	 */
	private HttpRequest readRequest() throws IOException {
		String requestLine = readLine();
		
		if (requestLine == null) {
			return null;
		}
		
		String[] parts = requestLine.replaceAll("\\s+", " ").split(" ");
		String method = parts[0];
		String path = parts[1].replaceAll("%20", " ");
		String version = parts[2];
		
		HashMap<String, String> headers = new HashMap<>();
		
		while (true) {
			String line = readLine();
			
			if (line.length() == 0) {
				break;
			}
			
			parseHeader(headers, line);
		}
		
		String message = null;
		
		if (method.equals("POST") || method.equals("PUT")) {
			if (!headers.containsKey("content-length")) {
				return null;
			}
			
			int contentLength = Integer.parseInt(headers.get("content-length"));
			message = new String(readCount(contentLength), StandardCharsets.UTF_8);
		}
		
		return new HttpRequest(requestLine, method, path, version, headers, message);
	}
	
	/**
	 * Writes the appropriate response to the connection's output stream.
	 * 
	 * @param request
	 * 		The {@link HttpRequest} determining the type of the response
	 * 
	 * @return
	 * 		A boolean indicating whether the client requested to close the connection
	 * 
	 * @throws IOException
	 */
	public boolean writeResponse(HttpRequest request) throws IOException {
		if (!request.getVersion().equals("HTTP/1.1")) {
			writeResponse("text/html", 501);
			return false;
		}
		
		HashMap<String, String> headers = request.getHeaders();
		
		if (!headers.containsKey("host")) {
			writeResponse("text/html", 400);
			return false;
		}
		
		String method = request.getMethod();
		String path = request.getPath();
		
		if (path.endsWith("/")) {
			path += "index.html";
		}
		
		File file = new File(PATH + path);
		
		if (method.equals("HEAD") || method.equals("GET")) {
			if (!file.exists()) {
				if (method.equals("HEAD")) {
					writeResponseHeaders("text/html", 404);
				} else {
					writeResponse("text/html", 404);
				}
				
				return false;
			}
			
			boolean isModified = true;
			
			if (headers.containsKey("if-modified-since")) {
				String dateString = headers.get("if-modified-since");
				ZonedDateTime zdt = ZonedDateTime.parse(dateString, DateTimeFormatter.RFC_1123_DATE_TIME);
				long time = Date.from(zdt.toInstant()).getTime();
				long lastTime = file.lastModified();
				
				isModified = (time < lastTime);
			}
			
			if (isModified) {
				String mime = getMIME(path);
				byte[] content = Files.readAllBytes(Paths.get(file.getPath()));
				
				if (method.equals("HEAD")) {
					writeResponseHeaders(mime, 200, content);
				} else {
					writeResponse(mime, 200, content);
				}
			} else {
				writeResponseHeaders("text/html", 304, null, new Date(file.lastModified()));
			}
		} else {
			String message = request.getBody();
			
			if (method.equals("PUT")) {
				writeTextFile(PATH + path, message);
				writeResponse("text/plain", 200, message.getBytes());
			}
			else if (file.exists()) {
				String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
				writeTextFile(PATH + path, content + message);
				writeResponse("text/plain", 200, (content + message).getBytes());
			}
			else {
				writeResponse("text/html", 404);
			}
		}
		
		return headers.containsKey("connection") && headers.get("connection").equals("close");
	}
	
	/**
	 * Writes the response only containing headers to the connection's output stream.
	 * 
	 * @param mime
	 * 		The MIME type of the response
	 * @param statusCode
	 * 		The status code of the response
	 * 
	 * @throws IOException
	 */
	private void writeResponseHeaders(String mime, int statusCode) throws IOException {
		writeResponse(mime, statusCode, null, null, true);
	}
	
	/**
	 * Writes the response only containing headers to the connection's output stream.
	 * 
	 * @param mime
	 * 		The MIME type of the response
	 * @param statusCode
	 * 		The status code of the response
	 * @param message
	 * 		The message of the response
	 * 
	 * @throws IOException
	 */
	private void writeResponseHeaders(String mime, int statusCode, byte[] message) throws IOException {
		writeResponse(mime, statusCode, message, null, true);
	}
	
	/**
	 * Writes the response only containing headers to the connection's output stream.
	 * 
	 * @param mime
	 * 		The MIME type of the response
	 * @param statusCode
	 * 		The status code of the response
	 * @param message
	 * 		The message of the response
	 * @param lastModified
	 * 		The date of the last modification of the resource composing the given message
	 * 
	 * @throws IOException
	 */
	private void writeResponseHeaders(String mime, int statusCode, byte[] message, Date lastModified) throws IOException {
		writeResponse(mime, statusCode, message, lastModified, true);
	}
	
	/**
	 * Writes the response (with body) to the connection's output stream.
	 * 
	 * @param mime
	 * 		The MIME type of the response
	 * @param statusCode
	 * 		The status code of the response
	 * 
	 * @throws IOException
	 */
	private void writeResponse(String mime, int statusCode) throws IOException {
		writeResponse(mime, statusCode, null, null, false);
	}
	
	/**
	 * Writes the response (with body) to the connection's output stream.
	 * 
	 * @param mime
	 * 		The MIME type of the response
	 * @param statusCode
	 * 		The status code of the response
	 * @param message
	 * 		The message of the response
	 * 
	 * @throws IOException
	 */
	private void writeResponse(String mime, int statusCode, byte[] message) throws IOException {
		writeResponse(mime, statusCode, message, null, false);
	}
	
	/**
	 * Writes the response to the connection's output stream.
	 * 
	 * @param mime
	 * 		The MIME type of the response
	 * @param statusCode
	 * 		The status code of the response
	 * @param message
	 * 		The message of the response
	 * @param lastModified
	 * 		The date of the last modification of the resource composing the given message
	 * @param HEAD
	 * 		A boolean that indicates whether it is a HEAD request (thus omitting the response body)
	 * 
	 * @throws IOException
	 */
	private void writeResponse(String mime, int statusCode, byte[] message, Date lastModified, boolean HEAD) throws IOException {
		String body = "<h1>200 OK</h1>";
		
		switch (statusCode) {
		case 200:
			writeLine("HTTP/1.1 200 OK");
			break;
			
		case 304:
			writeLine("HTTP/1.1 304 Not Modified");
			
			if (lastModified != null) {
			    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
			    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
			    
			    writeLine("Last-Modified: " + dateFormat.format(lastModified));
			}
			
			body = "<h1>304 Not Modified</h1>";
			break;
			
		case 400:
			writeLine("HTTP/1.1 400 Bad Request");
			body = "<h1>400 Bad Request</h1>";
			break;
			
		case 404:
			writeLine("HTTP/1.1 404 Not Found");
			body = "<h1>404 Not Found</h1>";
			break;
			
		case 501:
			writeLine("HTTP/1.1 501 Not Implemented");
			body = "<h1>501 Not Implemented</h1>";
			break;
		
		case 500:
		default:
			writeLine("HTTP/1.1 500 Server Error");
			body = "<h1>500 Server Error</h1>";
			break;
		}
		
		if (message == null) {
			message = String.join("\n", new String[]{
				"<!DOCTYPE html>",
				"<head>",
				"  <meta charset='utf-8'>",
				"  <title>" + statusCode + "</title>",
				"</head>",
				"<body>",
				"",
				body,
				"",
				"</body>",
				"</html>"
			}).getBytes();
		}
		
		String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
		
		writeLine("Date: " + date);
		writeLine("Content-Type: " + mime);
		writeLine("Content-Length: " + message.length);
		writeLine("");
		
		if (!HEAD) {
			response.write(message);
		}
		
		response.flush();
	}
	
	/**
	 * Closes the HTTP connection.
	 * After this call, the connection cannot be used anymore.
	 * 
	 * @throws IOException
	 */
	private void close() {
		try {
			request.close();
			response.close();
			socket.close();
		} catch (IOException e) {}
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
			int o = request.read();
			
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
			body[i] = (byte) request.read();
		}
		
		return body;
	}
	
	/**
	 * Writes a line to the connection's output stream.
	 * 
	 * @param string
	 * 		The line to write
	 * 
	 * @throws IOException
	 */
	private void writeLine(String string) throws IOException {
		byte[] bytes = (string + "\r\n").getBytes();
		response.write(bytes);
	}
	
	/**
	 * Returns the MIME type of the given extension name.
	 * 
	 * @param name
	 * 		The name of a resource extension
	 * 
	 * @return
	 * 		The MIME type of the given extension name
	 */
	private String getMIME(String name) {
		int index = name.lastIndexOf(".");
		
		if (index < 0) {
			return "application/octet-stream";
		}
		
		String extension = name.substring(index + 1);
		
		switch (extension) {
		case "html":
			return "text/html";
			
		case "txt":
			return "text/plain";
			
		case "png":
			return "image/png";
		
		case "jpg":
			return "image/jpeg";
			
		case "gif":
			return "image/gif";
			
		case "bmp":
			return "image/bmp";
			
		case "mp3":
			return "audio/mpeg";
			
		case "mp4":
			return "video/mp4";
			
		default:
			return "application/octet-stream";
		}
	}
	
	/**
	 * Writes a text file with the given content to the given relative path.
	 * 
	 * @param path
	 * 		The relative path to write the file to	
	 * @param content
	 * 		The textual content to write to the file
	 */
	private void writeTextFile(String path, String content) {
		try {
			File file = new File(path);
			file.getParentFile().mkdirs();
			
			FileWriter out = new FileWriter(file);
			out.write(content);
			out.close();
			
			System.out.println("TEXT FILE CREATED: " + path);
		} catch (Exception e) {
			System.out.println("Error: couldn't save text file '" + path + "'.");
		}
	}
}
