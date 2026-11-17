package com.banking.ui;

import com.banking.models.AccountStatus;
import com.banking.models.AuthenticatedEmployee;
import com.banking.models.BankAccount;
import com.banking.models.Customer;
import com.banking.repositories.AccountRepository;
import com.banking.repositories.CustomerRepository;
import com.banking.repositories.EmployeeRepository;
import com.banking.security.Authorization;
import com.banking.security.Permission;
import com.banking.services.AccountService;
import com.banking.services.AuditService;
import com.banking.services.CustomerService;
import com.banking.services.EmployeeAccess;
import com.mongodb.client.MongoDatabase;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** UI only. Services enforce permissions and validation again, using current database state. */
public final class CustomerAccountView {
    private final AuthenticatedEmployee actor;
    private final Runnable back;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final Label feedback = new Label();
    private final TableView<Customer> customers = new TableView<>();
    private final TableView<BankAccount> accounts = new TableView<>();
    private final TextField customerQuery = new TextField();
    private final TextField accountQuery = new TextField();
    private final TextField fullName = new TextField();
    private final TextField email = new TextField();
    private final TextField phone = new TextField();
    private final TextField address = new TextField();
    private final DatePicker dateOfBirth = new DatePicker();
    private int customerPage = 0;
    private final Label accountHolder = new Label("Select an account to view the holder.");
    private final ComboBox<AccountStatus> status = new ComboBox<>(FXCollections.observableArrayList(AccountStatus.values()));

    public CustomerAccountView(MongoDatabase database, AuthenticatedEmployee actor, Runnable back) {
        this.actor = actor;
        this.back = back;
        var access = new EmployeeAccess(new EmployeeRepository(database));
        var audit = new AuditService(database);
        var customersRepo = new CustomerRepository(database);
        customerService = new CustomerService(customersRepo, access, audit);
        accountService = new AccountService(new AccountRepository(database), customersRepo, access, audit);
    }

    public Parent build() {
        Label title = new Label("Customers & accounts"); title.getStyleClass().add("page-title");
        Button backButton = new Button("Back to dashboard"); backButton.setOnAction(e -> back.run());
        HBox heading = new HBox(15, title, new Region(), backButton);
        HBox.setHgrow(heading.getChildren().get(1), Priority.ALWAYS);

        configureCustomerTable();
        configureAccountTable();
        TabPane tabs = new TabPane(customerTab(), accountTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().forEach(t -> t.setClosable(false));
        feedback.setWrapText(true);
        VBox page = new VBox(14, heading, tabs, feedback);
        page.setPadding(new Insets(25));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        page.getStyleClass().add("form-card");
        BorderPane root = new BorderPane(page);
        root.setPadding(new Insets(16)); root.getStyleClass().add("app-background");
        searchCustomers();
        return root;
    }

    private void configureCustomerTable() {
        TableColumn<Customer, String> id = column("Customer ID", Customer::customerId);
        TableColumn<Customer, String> name = column("Name", Customer::fullName);
        TableColumn<Customer, String> emailCol = column("Email", Customer::email);
        TableColumn<Customer, String> phoneCol = column("Phone", Customer::phone);
        customers.getColumns().addAll(id, name, emailCol, phoneCol);
        customers.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        customers.setPrefHeight(220);
        customers.getSelectionModel().selectedItemProperty().addListener((obs, prev, current) -> {
            if (current == null) return;
            fullName.setText(current.fullName()); email.setText(current.email());
            phone.setText(current.phone()); address.setText(current.address());
            dateOfBirth.setValue(current.dateOfBirth());
            refreshAccounts(current.customerId());
        });
    }

    private void configureAccountTable() {
        TableColumn<BankAccount, String> number = column("Account number", BankAccount::accountNumber);
        TableColumn<BankAccount, String> owner = column("Customer ID", BankAccount::customerId);
        TableColumn<BankAccount, String> balance = column("Balance (NGN)",
            a -> com.banking.utils.Money.formatMinor(a.balanceMinor()));
        TableColumn<BankAccount, String> state = column("Status", a -> a.status().name());
        accounts.getColumns().addAll(number, owner, balance, state);
        accounts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        accounts.setPrefHeight(270);
        accounts.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                status.setValue(selected.status());
                String customerId = selected.customerId();
                async(() -> customerService.byId(actor, customerId),
                    found -> accountHolder.setText("Account holder: " + found.fullName()));
            }
        });
    }

    private Tab customerTab() {
        customerQuery.setPromptText("Name, email, phone, customer ID or account number");
        Button find = new Button("Search customers");
        find.setOnAction(e -> { customerPage = 0; searchCustomers(); });
        Button previous = new Button("Previous 50");
        previous.setOnAction(e -> { if (customerPage > 0) { customerPage--; searchCustomers(); } });
        Button next = new Button("Next 50");
        next.setOnAction(e -> { customerPage++; searchCustomers(); });
        HBox search = new HBox(10, customerQuery, find); HBox.setHgrow(customerQuery, Priority.ALWAYS);
        HBox pages = new HBox(10, previous, next);

        fullName.setPromptText("Full name"); email.setPromptText("Email address");
        phone.setPromptText("Phone number"); address.setPromptText("Residential address");
        dateOfBirth.setPromptText("Date of birth");
        Button reset = new Button("New customer / Clear form");
        reset.setOnAction(e -> { customers.getSelectionModel().clearSelection();
            fullName.clear(); email.clear(); phone.clear(); address.clear(); dateOfBirth.setValue(null); });
        Button create = new Button("Register customer"); create.getStyleClass().add("primary");
        Button save = new Button("Save selected customer");
        boolean canEdit = Authorization.can(actor.role(), Permission.MANAGE_CUSTOMERS);
        create.setDisable(!canEdit); save.setDisable(!canEdit);
        create.setOnAction(e -> {
            CustomerForm form = capture();
            async(() -> customerService.register(actor, form.name(), form.email(), form.phone(), form.address(), form.birthDate()),
                created -> { feedback.setText("Customer registered: " + created.customerId()); searchCustomers(); });
        });
        save.setOnAction(e -> {
            Customer selected = customers.getSelectionModel().getSelectedItem();
            if (selected == null) { feedback.setText("Choose a customer to update."); return; }
            CustomerForm form = capture();
            async(() -> customerService.update(actor, selected.customerId(), form.name(), form.email(),
                form.phone(), form.address(), form.birthDate()),
                updated -> { feedback.setText("Customer details saved."); searchCustomers(); });
        });
        GridPane fields = new GridPane(); fields.setHgap(10); fields.setVgap(10);
        fields.addRow(0, new Label("Full name"), fullName, new Label("Email"), email);
        fields.addRow(1, new Label("Phone"), phone, new Label("DOB"), dateOfBirth);
        fields.addRow(2, new Label("Address"), address);
        ColumnConstraints growing = new ColumnConstraints(); growing.setHgrow(Priority.ALWAYS);
        ColumnConstraints growing2 = new ColumnConstraints(); growing2.setHgrow(Priority.ALWAYS);
        fields.getColumnConstraints().addAll(new ColumnConstraints(), growing, new ColumnConstraints(), growing2);
        HBox controls = new HBox(10, reset, create, save);
        VBox content = new VBox(12, new Label("Select a customer to edit; registration requires Bank Teller access."),
            search, customers, pages, fields, controls);
        content.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true);
        return new Tab("Customers", scroll);
    }

    private Tab accountTab() {
        Label instructions = new Label("Select a customer in the Customers tab to view or open their accounts.");
        instructions.setWrapText(true);
        Button open = new Button("Open account for selected customer"); open.getStyleClass().add("primary");
        open.setDisable(!Authorization.can(actor.role(), Permission.CREATE_ACCOUNTS));
        open.setOnAction(e -> {
            Customer selected = customers.getSelectionModel().getSelectedItem();
            if (selected == null) { feedback.setText("Select a customer in the Customers tab first."); return; }
            async(() -> accountService.open(actor, selected.customerId()), a -> {
                feedback.setText("Opened account: " + a.accountNumber() + " with ₦0.00 opening balance.");
                refreshAccounts(selected.customerId());
            });
        });
        accountQuery.setPromptText("Exact 10-digit account number");
        Button lookup = new Button("Find account");
        lookup.setOnAction(e -> {
            String number = accountQuery.getText().trim();
            async(() -> accountService.byNumber(actor, number), a -> {
                accounts.setItems(FXCollections.observableArrayList(a));
                feedback.setText("Account found.");
            });
        });
        HBox find = new HBox(10, accountQuery, lookup); HBox.setHgrow(accountQuery, Priority.ALWAYS);
        status.setValue(AccountStatus.ACTIVE);
        Button change = new Button("Update account status");
        change.setDisable(!Authorization.can(actor.role(), Permission.MANAGE_ACCOUNT_STATUS));
        change.setOnAction(e -> {
            BankAccount selected = accounts.getSelectionModel().getSelectedItem();
            if (selected == null) { feedback.setText("Select an account first."); return; }
            AccountStatus desired = status.getValue();
            async(() -> accountService.changeStatus(actor, selected.accountNumber(), desired), changed -> {
                feedback.setText("Account status updated to " + changed.status() + ".");
                refreshAccounts(changed.customerId());
            });
        });
        HBox actions = new HBox(10, status, change);
        VBox content = new VBox(14, instructions, open, find, accounts,
            accountHolder, new Label("Supervisor-only status control (closed accounts cannot reopen):"), actions);
        content.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true);
        return new Tab("Accounts", scroll);
    }

    private record CustomerForm(String name, String email, String phone, String address, LocalDate birthDate) { }
    private CustomerForm capture() {
        return new CustomerForm(fullName.getText(), email.getText(), phone.getText(),
            address.getText(), dateOfBirth.getValue());
    }

    private void searchCustomers() {
        String query = customerQuery.getText();
        async(() -> customerService.search(actor, query, customerPage, 50), rows -> {
            customers.setItems(FXCollections.observableArrayList(rows));
            feedback.setText(rows.size() + " customer(s) on page " + (customerPage + 1) + ".");
        });
    }

    private void refreshAccounts(String customerId) {
        async(() -> accountService.byCustomer(actor, customerId, 0, 100), rows -> {
            accounts.setItems(FXCollections.observableArrayList(rows));
        });
    }

    private <T> void async(Callable<T> action, Consumer<T> onSuccess) {
        feedback.setText("Working...");
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return action.call(); } };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            feedback.setText(error instanceof IllegalArgumentException || error instanceof SecurityException
                ? error.getMessage() : "Request failed. Check MongoDB connectivity and try again.");
        });
        Thread worker = new Thread(task, "customer-account-worker");
        worker.setDaemon(true); worker.start();
    }

    private static <T> TableColumn<T, String> column(String title, java.util.function.Function<T, String> getter) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(v -> new ReadOnlyStringWrapper(getter.apply(v.getValue())));
        return c;
    }
}
