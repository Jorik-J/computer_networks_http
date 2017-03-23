package main;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Represents an HTTP response. An instance contains the following information:<br><br>
 * <ul>
 * 	<li>An HTTP method (e.g. "GET")</li>
 *  <li>A status line (e.g. "HTTP/1.1 200 OK")</li>
 *  <li>A status code</li>
 *  <li>An associative array containing the headers (key: header field, value: header value)</li>
 *  <li>An optional body (may be null when absent)</li>
 * </lu>
 * <br>
 * 
 * @author Bauwen Demol (r0583318)
 * @author Jorik Jooken (r0588270)
 */
public class HttpResponse {
	
	private String method;
	private String statusLine;
	private int statusCode;
	private HashMap<String, String> headers;
	private byte[] body = null;
	
	public HttpResponse(String method, String statusLine, HashMap<String, String> headers, byte[] body) {
		this.method = method;
		this.statusLine = statusLine;
		this.statusCode = Integer.parseInt(statusLine.replaceAll("\\s+", " ").split(" ")[1]);
		this.headers = new HashMap<String, String>(headers);
		
		if (body != null) {
			this.body = body.clone();
		}
	}

	public String getMethod() {
		return method;
	}

	public String getStatusLine() {
		return statusLine;
	}
	
	public int getStatusCode() {
		return statusCode;
	}

	public HashMap<String, String> getHeaders() {
		return new HashMap<>(headers);
	}
	
	public boolean hasBody() {
		return body != null;
	}

	public byte[] getBody() {
		if (!hasBody()) {
			return null;
		}
		
		return body.clone();
	}
	
	public String getBodyAsText() {
		if (!hasBody()) {
			return null;
		}
		
		return new String(body, StandardCharsets.UTF_8);
	}
}
