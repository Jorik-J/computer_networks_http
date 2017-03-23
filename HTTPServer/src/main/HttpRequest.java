package main;

import java.util.HashMap;

/**
 * Represents an HTTP request. An instance contains the following information:<br><br>
 * <ul>
 *  <li>A request line (e.g. "GET / HTTP/1.1")</li>
 *  <li>An HTTP method (e.g. "GET")</li>
 *  <li>A resource path (e.g. "/index.html")</li>
 *  <li>An HTTP version (e.g. "HTTP/1.1")</li>
 *  <li>An associative array containing the headers (key: header field, value: header value)</li>
 *  <li>An optional body (may be null if absent)</li>
 * </ul>
 * <br>
 * 
 * @author Bauwen Demol (r0583318)
 * @author Jorik Jooken (r0588270)
 */
public class HttpRequest {
	
	private String requestLine;
	private String method;
	private String path;
	private String version;
	private HashMap<String, String> headers;
	private String body;
	
	/**
	 * Constructs an HttpRequest from the given components.
	 * 
	 * <b>Note:</b> it's up to the creator of this constructor to make sure that the method, path and version given
	 * are the same as in the given request line!
	 * 
	 * @param requestLine
	 * 		The request line (the first line of the request)
	 * @param method
	 * 		The HTTP method of the request
	 * @param path
	 * 		The relative resource path
	 * @param version
	 * 		The HTTP version
	 * @param headers
	 * 		The map containing the headers
	 * @param body
	 * 		The complete body of the request. May be null,
	 * 		indication it has no body (e.g. when HEAD or GET request is sent)
	 */
	public HttpRequest(String requestLine, String method, String path, String version, HashMap<String, String> headers, String body) {
		this.requestLine = requestLine;
		this.method = method;
		this.path = path;
		this.version = version;
		this.headers = new HashMap<>(headers);
		this.body = body;
	}

	/**
	 * Returns the request line.
	 * 
	 * @return
	 * 		The request line
	 */
	public String getRequestLine() {
		return requestLine;
	}

	/**
	 * Returns the HTTP method of the request.
	 * 
	 * @return
	 * 		The HTTP method of the request
	 */
	public String getMethod() {
		return method;
	}

	/**
	 * Returns the resource path of the request, relative to the host.
	 * 
	 * @return
	 * 		The relative resource path of the request
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Returns the HTTP version of the request.
	 * 
	 * @return
	 * 		The HTTP version of the request
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Returns the map containing the headers of the request.
	 * 
	 * @return
	 * 		A map containing the headers of the request
	 */
	public HashMap<String, String> getHeaders() {
		return new HashMap<>(headers);
	}
	
	/**
	 * Returns whether the request has a body.
	 * 
	 * @return
	 * 		Whether the request has a body
	 */
	public boolean hasBody() {
		return body != null;
	}
	
	/**
	 * Returns the body of the request.
	 * 
	 * @return
	 * 		The body of the request. May be null if the request has no body at all
	 */
	public String getBody() {
		return body;
	}
}
