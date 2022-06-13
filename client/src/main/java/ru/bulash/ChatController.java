package ru.bulash;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class ChatController implements Initializable {

    private User user = null;
    private final ArrayList<String> names = new ArrayList<>();

    @FXML
    private VBox mainPanel;

    @FXML
    private TextFlow chatArea;

    @FXML
    private ListView<String> contacts;

    @FXML
    private CheckBox messageAll;

    @FXML
    private TextField inputField;

    @FXML
    private Button btnSend;

    private Client clientService = null;

    public void mockAction(ActionEvent actionEvent) {
        System.out.println("mock");
    }

    public void connectAction(ActionEvent actionEvent) {
//        Alert alert = new Alert(
//                Alert.AlertType.INFORMATION,
//                "Проверка",
//                ButtonType.CLOSE
//        );
//        alert.setTitle("Информация");
//        alert.setHeaderText("Тоже информация");
//        alert.showAndWait();
        loginDialog();
    }

    public void closeApplication(ActionEvent actionEvent) {
        if (clientService != null)
            clientService.closeClient();
        Platform.exit();
    }

    public void showManual(ActionEvent event) {
        String manual = "https://docs.yandex.ru/docs/view?url=ya-disk%3A%2F%2F%2Fdisk%2FGeekBrains%20manual%20for%20chat-client.docx&name=GeekBrains%20manual%20for%20chat-client.docx&uid=26621504";
        try {
            ChatController.openWebpage(new URI(manual));
        } catch(URISyntaxException exc) {
            exc.printStackTrace();
        }
    }

    public void sendMessage(ActionEvent actionEvent) {
        String text = inputField.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        String actionText;

        String focused;
        boolean toAll = messageAll.isSelected();
        if (toAll) {
            focused = "всех";
            actionText = String.format("USERALL:|%s", text);
        } else {
            focused = contacts.getFocusModel().getFocusedItem();
            actionText = String.format("USER:|%s|%s", focused, text);
        }

        Text contact = new Text(String.format("для @%s : ", focused));
        contact.setFill(Color.BLUE);
        contact.setFont(Font.font("System", FontWeight.BOLD, 12));

        Text message = new Text(text + System.lineSeparator());
        contact.setFont(Font.font("System", FontWeight.NORMAL, 12));

        chatArea.getChildren().addAll(contact, message);

        inputField.clear();
        messageAll.setSelected(false);

        clientService.sendMessage(actionText);
    }

    public static boolean openWebpage(URI uri) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(uri);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        clientService = new Client(this);
        btnSend.setDisable(true);
    }

    protected void loginDialog() {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Диалог входа");
        dialog.setHeaderText("Присоединитесь к сети чата");

        ButtonType loginButtonType = new ButtonType("Вход", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField username = new TextField();
        username.setPromptText("Логин");
        PasswordField password = new PasswordField();
        password.setPromptText("Пароль");

        grid.add(new Label("Логин:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Пароль:"), 0, 1);
        grid.add(password, 1, 1);

        Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
        loginButton.setDisable(true);

        username.textProperty().addListener((observable, oldValue, newValue) -> {
            loginButton.setDisable(newValue.trim().isEmpty());
        });

        dialog.getDialogPane().setContent(grid);

		Platform.runLater(username::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new User(username.getText(), password.getText());
            }
            return null;
        });

        Optional<User> result = dialog.showAndWait();

        result.ifPresent(userEntered -> {
            //System.out.println("Username=" + userEntered.getNickname() + ", Password=" + userEntered.getPassword());
            this.user = userEntered;
        });

        try {
            clientService.connect(this.user);
            Application.window.setTitle(String.format("Чат пользователя @%s", this.user.getNickname()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Здесь входящие (от / через сервер) actions
    public void processAction(Action action) {
        Platform.runLater(() -> serverAction(action));
    }

    private void serverAction(@NotNull Action action) {
        String[] data = action.getData();
        String message = data[0];
        String to;
        switch (action.getCommand()) {
            case "ALL:" -> to = "от сервера всем : ";
            case "SERVER:" -> to = "от сервера вам лично : ";
            case "USERALL:" -> {
                to = String.format("от @%s всем пользователям : ", data[0]);
                message = data[1];
            }
            case "USER:" -> {
                message = data[1];
                to = String.format("от @%s : ", data[0]);
            }
            case "LIST:" -> {
                to = String.format("получен пользователь @%s", message);
                names.add(message);
                contacts.setItems(FXCollections.observableList(names));
                btnSend.setDisable(false);
                message = "";
            }
            // case "REMOVE:"
            default -> to = " : ";
        }

        Text contact = new Text(to);
        contact.setFill(Color.GREEN);
        contact.setFont(Font.font("System", FontWeight.BOLD, 12));

        Text messageText = new Text(message + System.lineSeparator());
        contact.setFont(Font.font("System", FontWeight.NORMAL, 12));

        chatArea.getChildren().addAll(contact, messageText);
    }
}