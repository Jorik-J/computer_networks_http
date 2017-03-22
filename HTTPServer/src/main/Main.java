package main;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
	
	public static void main(String[] args) throws IOException {
		
		// get the command line argument
		
		if (args.length < 1) {
			System.out.println("Error: expected 1 argument containing directory in 'files/'.");
			return;
		}
		
		String path = args[0];
		
		int port = 8000;
		
		if (args.length > 1) {
			port = Integer.parseInt(args[1]);
		}
		
		
		// setup the server
		
		ServerSocket serverSocket = new ServerSocket(port);
		System.out.println("Listening on port " + port + "...");
		
		try {
			while (true) {
				Socket socket = serverSocket.accept();
				new Thread(new HttpConnection(socket, path)).start();
			}
		} catch (IOException e) {
			serverSocket.close();
		}
	}
}
