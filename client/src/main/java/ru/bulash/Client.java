package ru.bulash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Scanner;

public class Client implements Runnable {
	private Socket clientSocket;
	private final Thread sendThread;
	private final Thread receiveThread;
	private final User user;
	private PrintWriter out;
	private BufferedReader in;
	private final Scanner sc = new Scanner(System.in);
	private final HashMap<String, User> userList = new HashMap<>();

	public Client(User user) {
		this.user = user;

		// Отправить сообщение от клиента на сервер
		sendThread = new Thread(new Runnable() {
			String message;

			@Override
			public void run() {
				if (clientIsAlive()) {
					authorize();
				}
				while (clientIsAlive()) {
					System.out.printf("(Клиент \"%s\") - введите сообщение > ", user.getNickname());
					message = sc.nextLine();
					composeMessage(message);
				}
			}
		});

		// Получить сообщение от сервера на клиент
		receiveThread = new Thread(new Runnable() {
			String message;

			@Override
			public void run() {
				try {
					while (clientIsAlive()) {
						try {
							message = in.readLine();
							if (parseInput(message)) {
								System.out.printf("(Клиент \"%s\") - введите сообщение > ", user.getNickname());
							}
						} catch (SocketException e) {
							e.printStackTrace();
							closeClient();
						}
					}
				} catch (IOException exc) {
					System.out.println("Ошибка получения сообщения от сервера на клиент");
					exc.printStackTrace();
				}
			}
		});
	}

	@Override
	public void run() {
		try {
			connect();
			System.out.println("Клиент чата стартовал...");
		} catch (IOException exc) {
			System.out.printf("(Клиент \"%s\") - ошибка старта / работы клиента\n", user.getNickname());
			exc.printStackTrace();
		}
	}

	private void connect() throws IOException {
		clientSocket = new Socket(Constants.CHAT_HOST, Constants.CHAT_PORT);
		this.out = new PrintWriter(clientSocket.getOutputStream());
		this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

		sendThread.start();
		receiveThread.start();
	}

	private boolean parseInput(String message) {
		if (message == null) return true;

		Scanner messageScanner = new Scanner(message);
		String prefix = messageScanner.next();
		String userNick;
		StringBuilder statusMessage = new StringBuilder();
		StringBuilder userMessage = new StringBuilder();
		int status = 200;

		try {
			switch (prefix) {
				case "REMOVE:" -> {	// Сервер сообщил о выходе другого клиента
					userNick = messageScanner.next();
					if (userList.get(userNick) != null) {
						userList.remove(userNick);
					}
					statusMessage.append(String.format("пользователь @%s вышел из сети", userNick));
					throw new IllegalAccessException(statusMessage.toString());
				}
				case "ALL:" -> {    // Сообщение с сервера всем клиентам
					statusMessage.append("сообщение с сервера всем пользователям");
					while (messageScanner.hasNext())
						userMessage.append(messageScanner.next()).append(" ");
				}
				case "SERVER:" -> {    // Сообщение от сервера данному клиенту
					statusMessage.append("сообщение с сервера данному клиенту");
					while (messageScanner.hasNext())
						userMessage.append(messageScanner.next()).append(" ");
				}
				case "USER:" -> {    // Сообщение от другого клиента данному клиенту
					userNick = messageScanner.next();
					statusMessage.append(String.format("сообщение от пользователя @%s", userNick));
					while (messageScanner.hasNext())
						userMessage.append(messageScanner.next()).append(" ");
				}
				case "USERALL:" -> {    // Сообщение от другого клиента всем пользователям
					userNick = messageScanner.next();
					statusMessage.append(String.format("сообщение от пользователя @%s всем клиентам", userNick));
					while (messageScanner.hasNext())
						userMessage.append(messageScanner.next()).append(" ");
				}
				case "LIST:" -> {	// Получение пользователя из списка пользователей на сервере
					userNick = messageScanner.next();
					if (userList.get(userNick) == null) {
						userList.put(userNick, new User(userNick, ""));
					}
					statusMessage.append(String.format("получили с сервера пользователя @%s", userNick));
				}
				case "REG:" -> {    // Ответ на регистрацию
					status = messageScanner.nextInt();
					if (status == 200) {
						statusMessage.append("успешная регистрация на сервере");
						sendThread.setName("client-" + user.getNickname() + "-send");
						receiveThread.setName("client-" + user.getNickname() + "-receive");
					} else {
						statusMessage.append("ошибка регистрации клиента на сервере (");
						while (messageScanner.hasNext())
							statusMessage.append(messageScanner.next()).append(" ");
					}
				}
				default -> throw new IllegalArgumentException("Неизвестный префикс входящего сообщения");
			}

			if (userMessage.toString().equals("")) {
				System.out.printf("\n(Клиент \"%s\") - %s\n", user.getNickname(), statusMessage);
			} else {
				System.out.printf("\n(Клиент \"%s\") - %s > %s\n", user.getNickname(), statusMessage, userMessage);
			}
			if (status != 200) {
				throw new IllegalAccessException(userMessage.toString());
			}

			return true;
		} catch (IllegalAccessException exc) {
			closeClient();
			return false;
		} catch (Exception exc) {
			// TODO - послать сообщение серверу об ошибке в исходном сообщении
			System.out.printf("(Клиент \"%s\") - ошибка разбора входящего сообщения (\"%s\")", user.getNickname(), message);
			return true;
		}
	}

	private void closeClient() {
		try {
			out.println("CLOSE:");
			out.flush();
			clientSocket.close();
			sendThread.interrupt();
			receiveThread.interrupt();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				//
			}
			System.exit(0);
		} catch (IOException exc) {
			System.out.printf("(Клиент \"%s\") - не получается закрыть клиентский сокет\n", user.getNickname());
			exc.printStackTrace();
		}
	}

	private boolean clientIsAlive() {
		return !Thread.currentThread().isInterrupted() && !clientSocket.isClosed();
	}

	private void authorize() {
		out.printf("REG: %s %s\n", user.getNickname(), user.getPassword());
		out.flush();
	}

	private void composeMessage(String message) {
		Scanner messageScanner = new Scanner(message);
		String prefix = messageScanner.next();
		StringBuilder statusMessage = new StringBuilder();
		boolean internal = false;

		try {
			switch (prefix) {
				case Constants.PREFIX_END -> {
					System.out.printf("(Клиент \"%s\") - закрыт по команде пользователя\n", user.getNickname());
					closeClient();
					internal = true;
				}
				case Constants.PREFIX_HELP -> {
					//
					internal = true;
				}
			}
		} catch (RuntimeException exc) {
			System.out.println(exc.getMessage());
		}

		if (!internal) {
			out.println(message);
			out.flush();
		}
	}
}
