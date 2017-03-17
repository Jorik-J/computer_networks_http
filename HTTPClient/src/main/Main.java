package main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class Main {
	
	/*
	 * 
	 * Vragen:
	 * bedoelen ze met "embedded objects" enkel diegene die relatief zijn aan de website zelf,
	 * maw diegene die op dezelfde server staan en via HTTP/1.1 direct ook kunnen worden opgehaald?
	 * In dat geval moet implementatie nog wel aangepast worden (geen aparte requests sturen!)
	 * (Zou logisch zijn om hierarchische mappenstructuur op computer op te slaan en daarna terug te
	 * gebruiken voor de server, die de website opnieuw zou willen "uitzenden")
	 * 
	 * Bijhouden wanneer file voor het laatst is aangepast: moeten we kijken in de properties
	 * van die file? (voor if-modified-since header)
	 * 
	 * 
	 * TODO:
	 * HTTP/1.0: Not Supported teruggeven
	 * Relatieve paden enkel beschouwen: hou socket open (HTTP/1.1!)
	 * Geen rekening houden met "speciale" gevallen (verkeerde geformatteerde dingen) -- wordt getest met browser
	 * not-modified-since header implementeren (kijken naar bestand-properties)
	 * 
	 */
	
	public static String PATH = "files/";

	public static void main(String[] args) {
		
		// get command line arguments
		
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
		
		int port = 80;
		
		if (args.length > 2) {
			port = Integer.parseInt(args[2]);
		}
		
		
		// send the HTTP request
		
		HttpResponse response;
		
		switch (command) {
		case "HEAD":
			response = Http.HEAD(uri, port);
			break;
			
		case "GET":
			response = Http.GET(uri, port);
			break;
			
		case "POST":
		case "PUT":
			Scanner reader = new Scanner(System.in);
			System.out.println("Please enter your message:");
			String message = reader.nextLine();
			
			if (command.equals("POST")) {
				response = Http.POST(uri, port, message);
			} else {
				response = Http.PUT(uri, port, message);
			}
			
			break;
			
		default:
			System.out.println("Error: invalid command '" + command + "' (expected 'HEAD', 'GET', 'POST' or 'PUT').");
			return;
		}
		
		if (response == null) {
			System.out.println("Error: something went wrong handling the request.");
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
			
			
			// search for embedded objects and save to local files
			
			searchAndSave(uri.getHost(), response);
		}
	}
	
	public static void searchAndSave(String directory, HttpResponse response) {
		if (response == null) {
			return;
		}
		
		HashMap<String, String> headers = response.getHeaders();
		String contentType = headers.get("content-type");
		
		if (contentType == null) {
			contentType = "application/octet-stream";
		} else {
			int index = contentType.indexOf(";");
			if (index >= 0) {
				contentType = contentType.substring(0, index).trim();
			}
		}
		
		URI uri = response.getURI();
		String base = parseBase(uri.getPath());
		
		if (!isTextType(contentType)) {
			writeBinaryFile(directory + "/" + base, response.getBody());
			return;
		}
		
		String content = response.getBodyAsText();
		writeTextFile(directory + "/" + base, content);
		
		if (!contentType.equals("text/html")) {
			return;
		}
		
		ArrayList<String> objects = new ArrayList<>();
		
		searchImages(objects, content);
		//searchHTML? (<iframe>)
		//searchAudio? (<audio>)
		//searchVideo? (<video>)
		
		for (String object : objects) {
			String protocol = uri.getScheme() + "://";
			String host = uri.getHost();
			String absolutePath = getAbsolutePath(protocol + host, object);
			
			try {
				uri = new URI(absolutePath);
			} catch (URISyntaxException e) {
				e.printStackTrace();
				System.out.println("Invalid object uri found: " + absolutePath);
				return;
			}
			
			int port = uri.getPort();
			HttpResponse objectResponse = Http.GET(uri, port < 0 ? 80 : port);
			
			searchAndSave(directory, objectResponse);
		}
	}
	
	public static void searchImages(ArrayList<String> objects, String html) {
		String tag = searchTag(html, "img");
		
		while (tag != null) {
			String[] parts = tag.split(" ");
			
			for (String part : parts) {
				if (part.startsWith("src")) {
					String[] src = part.split("="); // TODO: kan spatie voor of achter bevatten
					
					if (src.length == 2) {
						String uri = src[1].trim();
						objects.add(uri.substring(1, uri.length() - 1));
					}
				}
			}
			
			html = html.substring(html.indexOf("<img") + 4);
			tag = searchTag(html, "img");
		}
	}
	
	public static String searchTag(String html, String tagName) {
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
	
	public static boolean isTextType(String mime) {
		return mime.split("/")[0].equals("text");
	}
	
	public static String parseBase(String path) {
		String base = path.substring(path.lastIndexOf("/") + 1);
		
		if (base.equals("")) {
			return "index.html";
		}
		
		return base;
	}
	
	public static String getAbsolutePath(String prefix, String name) {
		String absolutePath = name;
		
		int index = absolutePath.indexOf("://");
		if (index < 0) {
			absolutePath = Paths.get(prefix, absolutePath).toString();
		}
		
		return absolutePath;
	}
	
	public static void writeTextFile(String path, String content) {
		try {
			File file = new File(PATH + path);
			file.getParentFile().mkdirs();
			
			FileWriter out = new FileWriter(file);
			out.write(content);
			out.close();
			
			System.out.println("TEXT FILE CREATED: " + PATH + path);
		} catch (Exception e) {
			System.out.println("Error: couldn't save text file '" + path + "'.");
		}
	}
	
	public static void writeBinaryFile(String path, byte[] content) {
		try {
			File file = new File(PATH + path);
			file.getParentFile().mkdirs();
			
			FileOutputStream out = new FileOutputStream(file);
		    out.write(content);
		    out.close();
		    
		    System.out.println("BINARY FILE CREATED: " + PATH + path);
		} catch (Exception e) {
			System.out.println("Error: couldn't save binary file '" + path + "'.");
		}
	}
}
