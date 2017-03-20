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
		
		
		// setup the server
		
		ServerSocket serverSocket = new ServerSocket(8000);
		System.out.println("Listening on port 8000...");
		
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
