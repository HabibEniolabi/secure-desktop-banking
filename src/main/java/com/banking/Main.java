package com.banking;

import com.banking.config.AppConfig;
import com.banking.config.MongoContext;
import com.banking.models.AuthenticatedEmployee;
import com.banking.repositories.EmployeeRepository;
import com.banking.services.AuditService;
import com.banking.services.AuthService;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/** Minimal functional login/dashboard. Business modules arrive in separate PRs. */
public final class Main extends Application {
    private Stage window;
    private MongoContext mongo;

    @Override public void start(Stage stage) {
        this.window = stage;
        window.setTitle("Secure Banking & Customer Care | Simulation");
        window.setMinWidth(760);
        window.setMinHeight(520);
        window.setOnCloseRequest(e -> closeMongo());
        showLogin();
        window.show();
    }

    private void showLogin() {
        Label title = new Label("Secure Banking");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Employee sign-in · Educational simulation");
        subtitle.getStyleClass().add("subtle");
        TextField login = new TextField();
        login.setPromptText("Email or username");
        login.setMaxWidth(340);
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        password.setMaxWidth(340);
        Button signIn = new Button("Sign in");
        signIn.setDefaultButton(true);
        signIn.getStyleClass().add("primary");
        Label message = new Label();
        message.getStyleClass().add("error");
        message.setWrapText(true);
        message.setMaxWidth(340);

        signIn.setOnAction(e -> {
            signIn.setDisable(true);
            message.setText("Connecting securely...");
            // Do not hold a String copy of the password beyond the login request.
            String suppliedPassword = password.getText();
            String suppliedLogin = login.getText().trim();
            Task<AuthenticatedEmployee> task = new Task<>() {
                @Override protected AuthenticatedEmployee call() {
                    if (mongo == null) {
                        mongo = new MongoContext(AppConfig.fromEnvironment());
                        mongo.ensureIndexes();
                    }
                    AuthService auth = new AuthService(new EmployeeRepository(mongo.database()),
                        new AuditService(mongo.database()));
                    return auth.login(suppliedLogin, suppliedPassword);
                }
            };
            task.setOnSucceeded(done -> {
                password.clear();
                showDashboard(task.getValue());
            });
            task.setOnFailed(done -> {
                password.clear();
                signIn.setDisable(false);
                Throwable problem = task.getException();
                // Never display raw driver exceptions with database host/URI details.
                message.setText(problem instanceof SecurityException
                    ? "Invalid email/username or password."
                    : "Unable to sign in. Check configuration or database connectivity.");
            });
            Thread worker = new Thread(task, "banking-login-worker");
            worker.setDaemon(true);
            worker.start();
        });

        VBox form = new VBox(14, title, subtitle, new Label("Email or username"), login,
            new Label("Password"), password, signIn, message);
        form.setAlignment(Pos.CENTER_LEFT);
        form.getStyleClass().add("form-card");
        form.setMaxWidth(430);
        StackPane root = new StackPane(form);
        root.getStyleClass().add("app-background");
        root.setPadding(new Insets(32));
        setScene(root);
    }

    private void showDashboard(AuthenticatedEmployee employee) {
        Label title = new Label("Welcome, " + employee.fullName());
        title.getStyleClass().add("page-title");
        Label role = new Label("Signed in as " + employee.role().name().replace('_', ' '));
        role.getStyleClass().add("subtle");
        Label content = new Label("Authentication is working. Customer, account and banking screens are added in subsequent PRs.");
        content.setWrapText(true);
        content.setMaxWidth(620);
        Button logout = new Button("Log out");
        logout.setOnAction(e -> showLogin());
        HBox top = new HBox(16, title, new Region(), logout);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox page = new VBox(18, top, role, new Separator(), content);
        page.setPadding(new Insets(34));
        page.getStyleClass().add("form-card");
        BorderPane root = new BorderPane(page);
        root.getStyleClass().add("app-background");
        root.setPadding(new Insets(26));
        setScene(root);
    }

    private void setScene(javafx.scene.Parent root) {
        Scene scene = new Scene(root, 900, 620);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        window.setScene(scene);
    }

    private void closeMongo() {
        if (mongo != null) { mongo.close(); mongo = null; }
    }

    @Override public void stop() { closeMongo(); }

    public static void main(String[] args) {
        launch(args);
    }
}
