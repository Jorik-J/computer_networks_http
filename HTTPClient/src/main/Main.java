package main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

/**
 * The client's program class containing the main function.
 * 
 * @author Bauwen Demol (r0583318)
 * @author Jorik Jooken (r0588270)
 */
public class Main {
	
	private static final String PATH = "files/";
	
	private static HttpConnection connection;
	private static String host;
	
	/**
	 * The main function of the client program.
	 * 
	 * Expects at least 2 command-line arguments (the HTTP method and the URI) and
	 * optionally a third argument indicating the port number.
	 * 
	 * @param args
	 * 		The command-line arguments
	 * 
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		
		// get the command line arguments
		
		if (args.length < 2) {
			System.out.println("Error: expected at least 2 arguments.");
			return;
		}
		
		String command = args[0];
		String uriString = args[1];
		
		int index = uriString.indexOf("://");
		if (index < 0) {
			uriString = "http://" + uriString;
		}
		
		URI uri;
		
		try {
			uri = new URI(uriString);
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
		
		try {
			connection = new HttpConnection(host, port);	
		} catch (UnknownHostException e) {
			System.out.println("Error: invalid URI given.");
			return;
		}
		
		
		// send the request
		
		String path = uri.getPath();
		HttpResponse response;
		
		if (path.length() == 0) {
			path = "/";
		}
		
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
	
	/**
	 * Saves the body of the given response to the given relative path.
	 * If the content-type is HTML, it searches for embedded images and GETs them iteratively.
	 * 
	 * @param response
	 * 		The {@link HttpResponse} to use
	 * @param path
	 * 		The relative path to save the body to
	 */
	private static void saveAndSearch(HttpResponse response, String path) {
		if (!response.hasBody()) {
			return;
		}
		
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
	
	/**
	 * Searches for relative URI's of embedded images inside the given HTML string and
	 * returns them in a list.
	 * 
	 * More specifically, this function returns the value of the "src" and "lowsrc" attributes
	 * found in "img" tags.
	 * 
	 * @param html
	 * 		The HTML string to scan
	 * 
	 * @return
	 * 		A list containing all the relative image URI's found
	 */
	private static ArrayList<String> searchImages(String html) {
		ArrayList<String> images = new ArrayList<>();
		String tag = searchTag(html, "img");
		
		while (tag != null) {
			while (true) {
				int index = tag.toLowerCase().indexOf("src");
				
				if (index < 0) {
					break;
				}
				
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
				
				tag = tag.substring(index + 3);
			}
			
			html = html.substring(html.toLowerCase().indexOf("<img") + 4);
			tag = searchTag(html, "img");
		}
		
		return images;
	}
	
	/**
	 * Searches for a given tag name within the given HTML string and
	 * returns the content found within the opening tag.
	 * 
	 * @param html
	 * 		The HTML string to scan
	 * @param tagName
	 * 		The HTML tag name to search
	 * 
	 * @return
	 * 		The raw content found within the opening tag (excluding the angle brackets and the tag name itself)
	 * 		or null if no tag is found
	 */
	private static String searchTag(String html, String tagName) {
		int index = html.toLowerCase().indexOf("<" + tagName);
		
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
	
	/**
	 * Returns whether the given MIME string represents a textual type.
	 * 
	 * @param mime
	 * 		The MIME string to check
	 * 
	 * @return
	 * 		Whether the given MIME string represents a textual type.
	 */
	private static boolean isTextType(String mime) {
		return mime.split("/")[0].equals("text");
	}
	
	/**
	 * Returns the given name with its extension changed to .html if
	 * the content type is HTML.
	 * 
	 * @param name
	 * 		The name of the resource to change the extension of
	 * @param contentType
	 * 		A MIME string representing the content type
	 * 
	 * @return
	 * 		The possibly modified resource name. It is only modified
	 * 		if the content type is HTML, otherwise it returns the original name
	 */
	private static String addExtension(String name, String contentType) {
		String fullname = name;
		
		if (contentType.equals("text/html")) {
			int index = name.lastIndexOf(".");
			fullname = (index < 0 ? name : name.substring(0, index)) + ".html";
		}
		
		return fullname;
	}
	
	/**
	 * Writes a text file with the given content to the given relative path.
	 * 
	 * @param path
	 * 		The relative path to write the file to	
	 * @param content
	 * 		The textual content to write to the file
	 */
	private static void writeTextFile(String path, String content) {
		try {
			path = path.replaceAll("%20", " ");
			
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
	
	/**
	 * Writes a binary file with the given content to the given relative path.
	 * 
	 * @param path
	 * 		The relative path to write the file to
	 * @param content
	 * 		The binary content to write to the file
	 */
	private static void writeBinaryFile(String path, byte[] content) {
		try {
			path = path.replaceAll("%20", " ");
			
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
