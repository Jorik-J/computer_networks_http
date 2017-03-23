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
 *  <li>An optional body (may be null if absent)</li>
 * </ul>
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
	
	/**
	 * Constructs an HttpResponse from the given components.
	 * 
	 * @param method
	 * 		The HTTP method the response was requested by
	 * @param statusLine
	 * 		The status line (the first line of the response)
	 * @param headers
	 * 		The map containing the headers
	 * @param body
	 * 		The complete body of the response. May be null,
	 * 		indicating it has no body (e.g. when HEAD request was sent)
	 */
	public HttpResponse(String method, String statusLine, HashMap<String, String> headers, byte[] body) {
		this.method = method;
		this.statusLine = statusLine;
		this.statusCode = Integer.parseInt(statusLine.replaceAll("\\s+", " ").split(" ")[1]);
		this.headers = new HashMap<String, String>(headers);
		
		if (body != null) {
			this.body = body.clone();
		}
	}

	/**
	 * Returns the HTTP method the response was requested by.
	 * 
	 * @return
	 * 		The HTTP method the response was requested by
	 */
	public String getMethod() {
		return method;
	}

	/**
	 * Returns the status line of the response.
	 * 
	 * @return
	 * 		The status line of the response
	 */
	public String getStatusLine() {
		return statusLine;
	}
	
	/**
	 * Returns the status code of the response. This is a part of the status line.
	 * 
	 * @return
	 * 		The HTTP method the response was requested by
	 */
	public int getStatusCode() {
		return statusCode;
	}

	/**
	 * Returns the map containing the headers of the response.
	 * 
	 * @return
	 * 		A map containing the headers of the response
	 */
	public HashMap<String, String> getHeaders() {
		return new HashMap<>(headers);
	}
	
	/**
	 * Returns whether the response has a body.
	 * 
	 * @return
	 * 		Whether the response has a body
	 */
	public boolean hasBody() {
		return body != null;
	}

	/**
	 * Returns the body of the response as an array of bytes (binary).
	 * 
	 * @return
	 * 		The body of the response as an array of bytes. May be null
	 * 		if the response has no body at all
	 */
	public byte[] getBody() {
		if (!hasBody()) {
			return null;
		}
		
		return body.clone();
	}
	
	/**
	 * Returns the body of the response as a string (textual).
	 * 
	 * @return
	 * 		The body of the response as a string (UTF-8 encoding).
	 * 		May be null if the response has no body at all
	 */
	public String getBodyAsText() {
		if (!hasBody()) {
			return null;
		}
		
		return new String(body, StandardCharsets.UTF_8);
	}
}
