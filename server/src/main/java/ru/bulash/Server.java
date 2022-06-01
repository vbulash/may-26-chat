package ru.bulash;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Scanner;

public class Server implements Runnable {
	private static ServerSocket serverSocket;
	private static Thread sendThread = null;
	public final static HashMap<String, User> userList = new HashMap<>();
	public final static HashMap<String, ClientProcess> clientList = new HashMap<>();
	private static final Scanner sc = new Scanner(System.in);

	public Server() {
		// Отправить сообщение с сервера клиенту
		sendThread = new Thread(new Runnable() {
			String message;

			@Override
			public void run() {
				while (true) {
					System.out.print("Сервер - введите сообщение > ");
					message = sc.nextLine();
					composeMessage(message);
				}
			}
		});
	}

	public static void main(String[] args) {
		Thread server = new Thread(new Server());
		server.start();
	}

	private boolean processIsAlive() {
		return !Thread.currentThread().isInterrupted() && !serverSocket.isClosed();
	}

	public static @Nullable User login(String nickname, String password) {
		User user = userList.get(nickname);
		if (user == null) return null;
		if (user.getPassword().equals(password)) {
			return user;
		} else {
			return null;
		}
	}

	public static @Nullable User register(String nickname, String password) {
		if (userList.get(nickname) != null) return null;

		User user = new User(nickname, password);
		userList.put(user.getNickname(), user);
		return user;
	}

	@Override
	public void run() {
		try (ServerSocket _serverSocket = new ServerSocket(Constants.CHAT_PORT)) {
			serverSocket = _serverSocket;
			System.out.println("Сервер чата стартовал...");
			sendThread.setName("server-send");
			sendThread.start();

			while (!serverSocket.isClosed()) {
				Socket clientSocket = serverSocket.accept();
				ClientProcess client = new ClientProcess(clientSocket);
				client.setName("client-" + client.getNickname());
				client.start();
			}
		} catch (IOException exc) {
			System.out.println("Ошибка старта / работы сервера");
			exc.printStackTrace();
		}
	}

	private void composeMessage(String message) {
		Scanner messageScanner = new Scanner(message);
		String prefix = messageScanner.next();
		StringBuilder userMessage = new StringBuilder();
		boolean internal = false;

		try {
			if (Constants.PREFIX_HELP.equals(prefix)) {	//
				internal = true;
			}
			switch (prefix) {
				case "ALL:" -> {    // Сообщение с сервера всем клиентам
					while (messageScanner.hasNext())
						userMessage.append(messageScanner.next()).append(" ");
					for (ClientProcess client : clientList.values()) {
						client.out.printf("ALL: %s\n", userMessage);
						client.out.flush();
					}
				}
				case "SERVER:" -> {	// Сообщение с сервера конкретному клиенту
					String userNick = messageScanner.next();
					ClientProcess client = clientList.get(userNick);
					if (client != null) {
						while (messageScanner.hasNext())
							userMessage.append(messageScanner.next()).append(" ");

						client.out.printf("SERVER: %s\n", userMessage);
						client.out.flush();
					}
				}
			}
		} catch (RuntimeException exc) {
			System.out.println(exc.getMessage());
		}
	}
}
