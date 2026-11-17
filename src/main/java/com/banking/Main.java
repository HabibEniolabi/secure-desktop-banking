package com.banking;

import com.banking.config.AppConfig;
import com.banking.config.MongoContext;
import com.banking.notifications.GmailConfig;
import com.banking.notifications.GmailSmtpSender;
import com.banking.notifications.NotificationWorker;
import com.banking.models.AuthenticatedEmployee;
import com.banking.repositories.EmployeeRepository;
import com.banking.services.AuditService;
import com.banking.services.AuthService;
import com.banking.services.EmployeeManagementService;
import com.banking.services.EmployeeManagementService.EmployeeSummary;
import com.banking.ui.CustomerAccountView;
import com.banking.ui.TransactionView;
import com.banking.ui.ApprovalView;
import com.banking.ui.TicketView;
import com.banking.ui.ReportView;
import com.banking.models.Role;
import com.banking.security.Authorization;
import com.banking.security.Permission;
import javafx.collections.FXCollections;
import javafx.beans.property.ReadOnlyStringWrapper;
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
    private NotificationWorker notificationWorker;

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
        stopNotifications();
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
                startNotifications();
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
        Label content = new Label("Use the modules below to manage employees, customers, accounts and financial transactions.");
        content.setWrapText(true);
        content.setMaxWidth(620);
        Button logout = new Button("Log out");
        logout.setOnAction(e -> showLogin());
        HBox top = new HBox(16, title, new Region(), logout);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
        top.setAlignment(Pos.CENTER_LEFT);
        Button employeesButton = new Button("Manage employees");
        employeesButton.setVisible(Authorization.can(employee.role(), Permission.MANAGE_EMPLOYEES));
        employeesButton.setManaged(employeesButton.isVisible());
        employeesButton.setOnAction(e -> showEmployeeManagement(employee));
        Button customerAccountsButton = new Button("Customers & accounts");
        customerAccountsButton.setVisible(Authorization.can(employee.role(), Permission.VIEW_CUSTOMERS));
        customerAccountsButton.setManaged(customerAccountsButton.isVisible());
        customerAccountsButton.setOnAction(e -> showCustomerAccounts(employee));
        Button transactionsButton = new Button("Financial transactions");
        transactionsButton.setVisible(Authorization.can(employee.role(), Permission.VIEW_TRANSACTIONS)
            || Authorization.can(employee.role(), Permission.CREDIT)
            || Authorization.can(employee.role(), Permission.DEBIT)
            || Authorization.can(employee.role(), Permission.TRANSFER));
        transactionsButton.setManaged(transactionsButton.isVisible());
        transactionsButton.setOnAction(e -> showTransactions(employee));
        Button approvalsButton = new Button("Supervisor approvals");
        approvalsButton.setVisible(Authorization.can(employee.role(), Permission.APPROVE_RESTRICTED_TRANSACTIONS));
        approvalsButton.setManaged(approvalsButton.isVisible());
        approvalsButton.setOnAction(e -> showApprovals(employee));
        Button customerCareButton = new Button("Customer care & complaints");
        customerCareButton.setVisible(Authorization.can(employee.role(), Permission.MANAGE_TICKETS));
        customerCareButton.setManaged(customerCareButton.isVisible());
        customerCareButton.setOnAction(e -> showCustomerCare(employee));
        Button reportsButton = new Button("Dashboard & reports");
        reportsButton.setVisible(Authorization.can(employee.role(), Permission.VIEW_REPORTS));
        reportsButton.setManaged(reportsButton.isVisible());
        reportsButton.setOnAction(e -> showReports(employee));
        VBox page = new VBox(18, top, role, new Separator(), content, employeesButton,
            customerAccountsButton, transactionsButton, approvalsButton, customerCareButton, reportsButton);
        page.setPadding(new Insets(34));
        page.getStyleClass().add("form-card");
        BorderPane root = new BorderPane(page);
        root.getStyleClass().add("app-background");
        root.setPadding(new Insets(26));
        setScene(root);
    }

    private void showCustomerAccounts(AuthenticatedEmployee actor) {
        setScene(new CustomerAccountView(mongo.client(), mongo.database(), actor, () -> showDashboard(actor)).build());
    }

    private void showTransactions(AuthenticatedEmployee actor) {
        setScene(new TransactionView(mongo.client(), mongo.database(), actor, () -> showDashboard(actor)).build());
    }

    private void showApprovals(AuthenticatedEmployee actor) {
        setScene(new ApprovalView(mongo.client(), mongo.database(), actor,
            () -> showDashboard(actor)).build());
    }

    private void showReports(AuthenticatedEmployee actor) {
        setScene(new ReportView(mongo.database(), actor,
            () -> showDashboard(actor)).build());
    }

    private void showCustomerCare(AuthenticatedEmployee actor) {
        setScene(new TicketView(mongo.client(), mongo.database(), actor,
            () -> showDashboard(actor)).build());
    }

    private void showEmployeeManagement(AuthenticatedEmployee actor) {
        // Services enforce permissions again using the current database role.
        Label heading = new Label("Employee management");
        heading.getStyleClass().add("page-title");
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("subtle");
        TextField query = new TextField();
        query.setPromptText("Search by employee name, email, username or ID");
        Button searchButton = new Button("Search");
        Button back = new Button("Back to dashboard");
        back.setOnAction(e -> showDashboard(actor));

        TableView<EmployeeSummary> table = new TableView<>();
        TableColumn<EmployeeSummary, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().fullName()));
        TableColumn<EmployeeSummary, String> email = new TableColumn<>("Email");
        email.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().email()));
        TableColumn<EmployeeSummary, String> role = new TableColumn<>("Role");
        role.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().role().name()));
        TableColumn<EmployeeSummary, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().active() ? "Active" : "Inactive"));
        table.getColumns().addAll(name, email, role, status);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TextField fullName = new TextField(); fullName.setPromptText("Full name");
        TextField employeeEmail = new TextField(); employeeEmail.setPromptText("Email");
        TextField username = new TextField(); username.setPromptText("Username (for new employees)");
        PasswordField password = new PasswordField(); password.setPromptText("Password (new employees, 12+ characters)");
        ComboBox<Role> roleSelection = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        roleSelection.setValue(Role.BANK_TELLER);
        CheckBox enabled = new CheckBox("Active"); enabled.setSelected(true);
        Button create = new Button("Create employee"); create.getStyleClass().add("primary");
        Button update = new Button("Save selected employee");
        Label selection = new Label("Select an employee to edit their name, email, role or status.");
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                fullName.setText(selected.fullName()); employeeEmail.setText(selected.email());
                username.setText(selected.username()); roleSelection.setValue(selected.role());
                enabled.setSelected(selected.active()); password.clear();
                selection.setText("Editing: " + selected.id());
            }
        });

        Runnable load = () -> {
            String searchText = query.getText();
            runEmployeeTask(() -> employeeManagement().search(actor, searchText, 0, 50),
                rows -> { table.setItems(FXCollections.observableArrayList(rows));
                    feedback.setText(rows.size() + " employee(s) shown (first 50)."); }, feedback);
        };
        searchButton.setOnAction(e -> load.run());
        create.setOnAction(e -> {
            String nameValue = fullName.getText(), emailValue = employeeEmail.getText();
            String usernameValue = username.getText(), passwordValue = password.getText();
            Role selectedRole = roleSelection.getValue();
            runEmployeeTask(() -> employeeManagement().create(actor, nameValue,
                    emailValue, usernameValue, passwordValue, selectedRole),
                result -> { password.clear(); feedback.setText("Created employee " + result.fullName()); load.run(); },
                feedback);
        });
        update.setOnAction(e -> {
            EmployeeSummary selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { feedback.setText("Select an employee first."); return; }
            String nameValue = fullName.getText(), emailValue = employeeEmail.getText();
            Role selectedRole = roleSelection.getValue();
            boolean isActive = enabled.isSelected();
            runEmployeeTask(() -> employeeManagement().update(actor, selected.id(), nameValue,
                    emailValue, selectedRole, isActive),
                result -> { feedback.setText("Employee saved."); load.run(); }, feedback);
        });
        HBox actions = new HBox(10, searchButton, back); actions.setAlignment(Pos.CENTER_LEFT);
        HBox formActions = new HBox(10, create, update); formActions.setAlignment(Pos.CENTER_LEFT);
        VBox form = new VBox(9, selection, fullName, employeeEmail, username, password,
            roleSelection, enabled, formActions);
        VBox page = new VBox(14, heading, query, actions, table, form, feedback);
        page.setPadding(new Insets(28)); page.getStyleClass().add("form-card");
        ScrollPane scroll = new ScrollPane(page); scroll.setFitToWidth(true);
        BorderPane root = new BorderPane(scroll); root.setPadding(new Insets(18));
        root.getStyleClass().add("app-background");
        setScene(root);
        load.run();
    }

    private EmployeeManagementService employeeManagement() {
        return new EmployeeManagementService(new EmployeeRepository(mongo.database()),
            new AuditService(mongo.database()));
    }

    private <T> void runEmployeeTask(java.util.concurrent.Callable<T> action,
                                     java.util.function.Consumer<T> onSuccess, Label status) {
        status.setText("Working...");
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return action.call(); }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            status.setText(error instanceof IllegalArgumentException || error instanceof SecurityException
                ? error.getMessage() : "Unable to complete request. Check database connectivity.");
        });
        Thread thread = new Thread(task, "employee-management-worker");
        thread.setDaemon(true); thread.start();
    }

    private void setScene(javafx.scene.Parent root) {
        Scene scene = new Scene(root, 900, 620);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        window.setScene(scene);
    }

    private void startNotifications() {
        if (notificationWorker != null || mongo == null) return;
        GmailConfig.fromEnvironment().ifPresent(config -> {
            notificationWorker = new NotificationWorker(mongo.database(), new GmailSmtpSender(config));
            notificationWorker.start();
        });
    }

    private void stopNotifications() {
        if (notificationWorker != null) {
            notificationWorker.close();
            notificationWorker = null;
        }
    }

    private void closeMongo() {
        stopNotifications();
        if (mongo != null) { mongo.close(); mongo = null; }
    }

    @Override public void stop() { closeMongo(); }

    public static void main(String[] args) {
        launch(args);
    }
}
