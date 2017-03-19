package main;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

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
