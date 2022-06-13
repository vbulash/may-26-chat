package ru.bulash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;

import static ru.bulash.Constants.*;

public class Client {
	private final ChatController chatController;
	public Action action = null;
	private Socket clientSocket;
	private final Thread receiveThread;
	private User user;
	private PrintWriter out;
	private BufferedReader in;
	private final HashMap<String, User> userList = new HashMap<>();

	public Client(ChatController chatController) {
		this.chatController = chatController;

		// Получить сообщение от сервера на клиент
		receiveThread = new Thread(new Runnable() {
			String message;

			@Override
			public void run() {
				try {
					while (clientIsAlive()) {
						try {
							message = in.readLine();
							parseInput(message);
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

	public void connect(User user) throws IOException {
		clientSocket = new Socket(CHAT_HOST, CHAT_PORT);
		this.out = new PrintWriter(clientSocket.getOutputStream());
		this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		this.user = user;

		authorize();
		receiveThread.start();
	}

	private boolean parseInput(String message) {
		if (message == null) return true;

		action = new Action(message);
		String command = action.getCommand();
		String[] data = action.getData();
		String statusMessage = "";
		boolean invisible = false;	// Невизуальное сообщение

		try {
			switch (command) {
				case CLIENT_REMOVE -> {	// Сервер сообщил о выходе другого клиента
					if (userList.get(data[0]) != null) {
						userList.remove(data[0]);
					}
					statusMessage = String.format("Пользователь @%s вышел из сети", data[0]);
				}
				case CLIENT_ALL -> statusMessage = "Сообщение с сервера всем пользователям";
				case CLIENT_SERVER -> statusMessage = "Сообщение с сервера данному клиенту";
				case CLIENT_USER -> statusMessage = String.format("Cообщение от пользователя @%s", data[0]);
				case CLIENT_USERALL -> statusMessage = String.format("Сообщение от пользователя @%s всем клиентам", data[0]);
				case CLIENT_LIST -> {	// Получение пользователя из списка пользователей на сервере
					if (userList.get(data[0]) == null && !data[0].equals(user.getNickname())) {
						userList.put(data[0], new User(data[0], ""));
					} else invisible = true;
					//statusMessage = String.format("Получили с сервера пользователя @%s", data[0]);
				}
				default -> throw new IllegalArgumentException("Неизвестный префикс входящего сообщения");
			}

			System.out.println(statusMessage);
			if (!invisible) {
				chatController.processAction(this.action);
			}
			return true;
		} catch (Exception exc) {
			// TODO - послать сообщение серверу об ошибке в исходном сообщении
			return true;
		}
	}

	public void closeClient() {
		try {
			out.println("CLOSE:");
			out.flush();
			clientSocket.close();
			receiveThread.interrupt();
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				//
			}
			System.exit(0);
		} catch (IOException exc) {
			System.out.println("Не получается закрыть клиентский сокет");
			exc.printStackTrace();
		}
	}

	private boolean clientIsAlive() {
		return !Thread.currentThread().isInterrupted() && !clientSocket.isClosed();
	}

	private void authorize() {
		out.printf("REG:|%s|%s\n", user.getNickname(), user.getPassword());
		out.flush();
	}

	public void sendMessage(String message) {
		action = new Action(message);
		String command = action.getCommand();

		boolean internal = false;

		try {
			switch (command) {
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
