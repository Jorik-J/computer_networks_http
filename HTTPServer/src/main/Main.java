package main;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
	
	public static void main(String[] args) throws IOException {
		ServerSocket serverSocket = new ServerSocket(8000);
		System.out.println("Listening on port 8000...");
		
		try {
			while (true) {
				Socket socket = serverSocket.accept();
				new Thread(new HttpConnection(socket)).start();
			}
		} catch (IOException e) {
			serverSocket.close();
		}
	}
}
