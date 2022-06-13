package ru.bulash;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ClientProcess extends Thread {
	private final Socket clientSocket;
	private final Thread sendThread;
	private final Thread receiveThread;
	public PrintWriter out;
	private BufferedReader in;
	private Scanner sc;
	private User user = null;

	public ClientProcess(Socket socket) {
		this.clientSocket = socket;

		// Отправить сообщение с сервера / через сервер на клиент
		sendThread = new Thread(new Runnable() {
			String message;

			@Override
			public void run() {
				try {
					negotiate();
					while (processIsAlive()) {
						try {
							message = in.readLine();
							composeMessage(message);
						} catch (RuntimeException e) {
							closeClient();
						}
					}
				} catch (IOException exc) {
					System.out.println("Ошибка получения сообщения с клиента на сервер");
					exc.printStackTrace();
				}
			}
		});

		// Получить сообщение с клиента на сервер
		receiveThread = new Thread(new Runnable() {
			String message;

			@Override
			public void run() {
				while (processIsAlive()) {
					try {
						message = sc.nextLine();
						parseInput(message);
					} catch (Exception e) {
						//
					}
				}
			}
		});
	}

	@Override
	public void run() {
		try {
			this.out = new PrintWriter(clientSocket.getOutputStream());
			this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			this.sc = new Scanner(this.in);

			sendThread.start();
			receiveThread.start();
		} catch (IOException exc) {
			System.out.println("Ошибка старта / работы процесса поддержки клиента");
			exc.printStackTrace();
		}
	}

	private boolean parseInput(String message) {
		if (message == null) return true;

		Action action = new Action(message);
		String command = action.getCommand();
		String[] data = action.getData();

		String userNick;
		String statusMessage;
		int status = 200;

		try {
			switch (command) {
				case "CLOSE:" -> {    // Клиент послал сообщение о своем закрытии
					userNick = user.getNickname();
					statusMessage = String.format("Пользователь @%s вышел из сети", userNick);

					// Закрыть процесс клиента на сервере
					for (ClientProcess client : Server.clientList.values()) {
						client.out.printf("REMOVE:|%s\n", user.getNickname());
						client.out.flush();
					}
				}
				case "USER:" -> {    // Сообщение от текущего клиента другому клиенту
					userNick = data[0];
					statusMessage = String.format("Сообщение для пользователя @%s", userNick);

					ClientProcess client = Server.clientList.get(userNick);
					if (client != null) {
						client.out.printf("USER:|%s|%s\n", user.getNickname(), data[1]);
						client.out.flush();
					}
				}
				case "USERALL:" -> {    // Сообщение от текущего клиента всем остальным клиентам
					userNick = user.getNickname();
					statusMessage = "Сообщение от текущего пользователя всем пользователям";

					for (ClientProcess client : Server.clientList.values()) {
						if (client != null && !client.user.getNickname().equals(this.user.getNickname())) {
							client.out.printf("USERALL:|%s|%s\n", userNick, data[0]);
							client.out.flush();
						}
					}
				}
				default -> throw new IllegalArgumentException("Неизвестный префикс входящего сообщения");
			}

			System.out.printf("\n(Клиент \"%s\") - %s\n", user.getNickname(), statusMessage);

			return true;
		} catch (Exception exc) {
			// TODO - послать сообщение серверу об ошибке в исходном сообщении
			System.out.printf("(Клиент \"%s\") - ошибка разбора входящего сообщения (\"%s\")", user.getNickname(), message);
			return true;
		}
	}

	private void closeClient() {
		try {
			clientSocket.close();
			sendThread.interrupt();
			receiveThread.interrupt();
		} catch (IOException exc) {
			System.out.printf("(Клиент \"%s\") - не получается закрыть клиентский сокет\n", user.getNickname());
			exc.printStackTrace();
		}
	}

	private boolean processIsAlive() {
		return !Thread.currentThread().isInterrupted() && !clientSocket.isClosed();
	}

	public boolean negotiate() {
		try {
			// Получить от клиента запрос регистрации
			String message = in.readLine();
			Action action = new Action(message);
			String prefix = action.getCommand();
			String[] data = action.getData();
			String nickname = data[0];
			String password = data[1];

			User current = Server.login(nickname, password);
			if (current != null) {
				this.user = current;
				sendThread.setName("client-" + user.getNickname() + "-send");
				receiveThread.setName("client-" + user.getNickname() + "-receive");
				Server.clientList.put(nickname, this);

				// Отдать всем клиентам полный список пользователей
				for (ClientProcess client : Server.clientList.values()) {
					for (User _current : Server.userList.values()) {
						if (_current.isActive() && !_current.getNickname().equals(client.user.getNickname())) {
							client.out.printf("LIST:|%s\n", _current.getNickname());
							client.out.flush();
						}
					}
				}
			}

			System.out.printf("\nСервер - пользователь @%s вошел в сеть\n", nickname);

			return true;
		} catch (IOException exc) {
			out.println("REG:|500");
			out.flush();
			exc.printStackTrace();
			return false;
		}
	}

	private void composeMessage(String message) {
		Action action = new Action(message);
		String prefix = action.getCommand();
		String[] data = action.getData();
		boolean internal = false;

		try {
			if (Constants.PREFIX_HELP.equals(prefix)) {    //
				internal = true;
			}
//			switch (prefix) {
//			}
		} catch (RuntimeException exc) {
			System.out.println(exc.getMessage());
		}

		if (!internal) {
			out.println(message);
			out.flush();
		}
	}

	public String getNickname() {
		if (user == null) return null;
		return user.getNickname();
	}
}
