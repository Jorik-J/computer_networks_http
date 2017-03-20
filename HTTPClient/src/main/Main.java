package main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Main {
	
	private static final String PATH = "files/";
	
	private static HttpConnection connection;
	private static String host;

	public static void main(String[] args) throws IOException {
		
		// get the command line arguments
		
		if (args.length < 2) {
			System.out.println("Error: expected at least 2 arguments.");
			return;
		}
		
		String command = args[0];
		
		URI uri;
		
		try {
			uri = new URI(args[1]);
		} catch (URISyntaxException e) {
			System.out.println("Error: invalid URI given.");
			return;
		}
		
		int port = uri.getPort();
		
		if (args.length > 2) {
			port = Integer.parseInt(args[2]);
		} else if (port < 0) {
			port = 80;
		}
		
		host = uri.getHost();
		connection = new HttpConnection(host, port);
		
		
		// send the request
		
		String path = uri.getPath();
		HttpResponse response;
		
		switch (command) {
		case "HEAD":
			response = connection.HEAD(path);
			break;
			
		case "GET":
			response = connection.GET(path);
			break;
			
		case "POST":
		case "PUT":
			Scanner reader = new Scanner(System.in);
			System.out.println("Please enter your message:");
			String message = reader.nextLine();
			
			if (command.equals("POST")) {
				response = connection.POST(path, message);
			} else {
				response = connection.PUT(path, message);
			}
			
			break;
			
		default:
			System.out.println("Error: invalid command '" + command + "' (expected 'HEAD', 'GET', 'POST' or 'PUT').");
			connection.close();
			return;
		}
		
		if (response == null) {
			System.out.println("Error: something went wrong handling the request.");
			connection.close();
			return;
		}
		
		
		// print the response
		
		System.out.println(response.getStatusLine());
		
		HashMap<String, String> headers = response.getHeaders();
		
		for (String name : headers.keySet()) {
			System.out.println(name + ": " + headers.get(name));
		}
		
		System.out.println("");
		
		if (response.hasBody()) {
			System.out.println(response.getBodyAsText());
		}
		
		
		// save to local file system and, if possible, search for embedded images
		
		if (command.equals("GET") && response.hasBody()) {
			saveAndSearch(response, path);
		}
		
		
		// close the connection
		
		connection.close();
	}
	
	private static void saveAndSearch(HttpResponse response, String path) {
		HashMap<String, String> headers = response.getHeaders();
		String contentType = headers.get("content-type");
		
		if (contentType == null) {
			contentType = "application/octet-stream";
		} else {
			contentType = contentType.split(";")[0].trim();
		}
		
		String filename = addExtension(host + path + (path.endsWith("/") ? "index.html" : ""), contentType);
		
		if (!isTextType(contentType)) {
			writeBinaryFile(filename, response.getBody());
			return;
		}
		
		String content = response.getBodyAsText();
		writeTextFile(filename, content);
		
		if (!contentType.equals("text/html")) {
			return;
		}
		
		ArrayList<String> images = searchImages(content);
		for (String image : images) {
			
			HttpResponse imageResponse = connection.GET(image);
			
			if (imageResponse != null) {
				saveAndSearch(imageResponse, image);
			}
		}
	}
	
	private static ArrayList<String> searchImages(String html) {
		ArrayList<String> images = new ArrayList<>();
		String tag = searchTag(html, "img");
		
		while (tag != null) {
			int index = tag.indexOf("src");
			int state = 0;
			String uri = "";
			String quote = "";
			
			for (int i = index + 3; i < tag.length(); i++) {
				char c = tag.charAt(i);
				
				if (state == 0) {
					if (c == '=') {
						state = 1;
					}
					else if (c != ' ') {
						break;
					}
				}
				else if (c == '"' || c == "'".charAt(0)) {
					if (quote.length() == 0) {
						quote += c;
						state = 2;
					}
					else if (quote.equals(c + "")) {
						state = 3;
						break;
					}
					else {
						uri += c;
					}
				}
				else if (state == 2) {
					uri += c;
				}
				else if (c != ' ') {
					break;
				}
			}
			
			if (state == 3 && uri.indexOf("://") < 0) {
				uri = uri.trim();
				
				if (!uri.startsWith("/")) {
					uri = "/" + uri;
				}
				
				images.add(uri);
			}
			
			html = html.substring(html.indexOf("<img") + 4);
			tag = searchTag(html, "img");
		}
		
		return images;
	}
	
	private static String searchTag(String html, String tagName) {
		int index = html.indexOf("<" + tagName);
		
		if (index < 0) {
			return null;
		}
		
		String tag = "";
		
		for (int i = index + tagName.length() + 1; i < html.length(); i++) {
			char c = html.charAt(i);
			
			if (c == '>') {
				return tag;
			}
			
			tag += c;
		}
		
		return null;
	}
	
	private static boolean isTextType(String mime) {
		return mime.split("/")[0].equals("text");
	}
	
	private static String addExtension(String name, String contentType) {
		String fullname = name;
		
		if (contentType.equals("text/html")) {
			int index = name.lastIndexOf(".");
			fullname = (index < 0 ? name : name.substring(0, index)) + ".html";
		}
		
		return fullname;
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
			System.out.println("Error: couldn't save text file '" + PATH + path + "'.");
		}
	}
	
	private static void writeBinaryFile(String path, byte[] content) {
		try {
			File file = new File(PATH + path);
			file.getParentFile().mkdirs();
			
			FileOutputStream out = new FileOutputStream(file);
		    out.write(content);
		    out.close();
		    
		    System.out.println("BINARY FILE CREATED: " + PATH + path);
		} catch (Exception e) {
			System.out.println("Error: couldn't save binary file '" + PATH + path + "'.");
		}
	}
}
