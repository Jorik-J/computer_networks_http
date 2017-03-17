package main;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
	
	// TODO: als geen host staat in HTTP, geef error terug (evt statuscode)

	public static void main(String[] args) throws IOException {
		@SuppressWarnings("resource")
		ServerSocket serverSocket = new ServerSocket(8000);
		System.out.println("Listening...");
		
		while (true) {
			Socket socket = serverSocket.accept();
			System.out.println("Someone connected!");
			
			new Thread(new HttpConnection(socket)).start();
		}
	}
}
