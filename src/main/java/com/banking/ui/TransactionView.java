package com.banking.ui;

import com.banking.models.AuthenticatedEmployee;
import com.banking.models.BankingRequest;
import com.banking.models.TransactionReceipt;
import com.banking.models.TransactionType;
import com.banking.repositories.EmployeeRepository;
import com.banking.repositories.TransactionRepository;
import com.banking.security.Authorization;
import com.banking.security.Permission;
import com.banking.services.EmployeeAccess;
import com.banking.services.PostingPolicy;
import com.banking.services.TransactionService;
import com.banking.utils.Money;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.ZoneOffset;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Desktop UI never mutates account balances. Every operation runs on a background thread. */
public final class TransactionView {
    private final AuthenticatedEmployee actor;
    private final Runnable back;
    private final TransactionService service;
    private final Label feedback = new Label();
    private final TextField requestId = new TextField(UUID.randomUUID().toString());
    private final TextField source = new TextField();
    private final TextField destination = new TextField();
    private final TextField amount = new TextField();
    private final ComboBox<TransactionType> operation = new ComboBox<>(
        FXCollections.observableArrayList(TransactionType.values()));
    private final TextArea receipt = new TextArea();
    private final TableView<TransactionReceipt> history = new TableView<>();
    private final TextField accountFilter = new TextField();
    private final ComboBox<String> typeFilter = new ComboBox<>(
        FXCollections.observableArrayList("All", "CREDIT", "DEBIT", "TRANSFER"));
    private final Label pageLabel = new Label("Page 1");
    private final DatePicker startDate = new DatePicker();
    private final DatePicker endDate = new DatePicker();
    private int page = 0;

    public TransactionView(MongoClient client, MongoDatabase database,
                           AuthenticatedEmployee actor, Runnable back) {
        this.actor = actor;
        this.back = back;
        service = new TransactionService(new TransactionRepository(client, database),
            new EmployeeAccess(new EmployeeRepository(database)), PostingPolicy.fromEnvironment());
    }

    public Parent build() {
        Label title = new Label("Financial transactions");
        title.getStyleClass().add("page-title");
        Button backButton = new Button("Back to dashboard");
        backButton.setOnAction(e -> back.run());
        HBox header = new HBox(16, title, new Region(), backButton);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);

        TabPane tabs = new TabPane();
        if (Authorization.can(actor.role(), Permission.CREDIT)
            || Authorization.can(actor.role(), Permission.DEBIT)
            || Authorization.can(actor.role(), Permission.TRANSFER)) {
            tabs.getTabs().add(new Tab("Credit / Debit / Transfer", postingTab()));
        }
        if (Authorization.can(actor.role(), Permission.VIEW_TRANSACTIONS)) {
            tabs.getTabs().add(new Tab("Transaction history", historyTab()));
        }
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        feedback.setWrapText(true);
        feedback.setMaxWidth(900);
        VBox page = new VBox(15, header, tabs, feedback);
        page.setPadding(new Insets(24));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        page.getStyleClass().add("form-card");
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        BorderPane root = new BorderPane(scroll);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("app-background");
        return root;
    }

    private Parent postingTab() {
        operation.setValue(TransactionType.CREDIT);
        source.setPromptText("Source account (10 digits)");
        destination.setPromptText("Destination account (10 digits)");
        amount.setPromptText("Amount in naira, e.g. 2500.00");
        requestId.setPromptText("Request UUID — keep this for retries");
        requestId.setPrefColumnCount(38);
        requestId.setTooltip(new Tooltip("Keep the SAME request ID when the outcome is unknown. " +
            "Never generate a new ID for a retry."));
        Button newId = new Button("New request ID");
        newId.setOnAction(e -> requestId.setText(UUID.randomUUID().toString()));
        HBox idRow = new HBox(9, requestId, newId);
        HBox.setHgrow(requestId, Priority.ALWAYS);
        operation.setOnAction(e -> updateAccountInputs());
        updateAccountInputs();

        Button submit = new Button("Submit financial transaction");
        submit.getStyleClass().add("primary");
        submit.setOnAction(e -> {
            final BankingRequest request;
            try {
                request = switch (operation.getValue()) {
                    case CREDIT -> BankingRequest.credit(requestId.getText(), destination.getText(), amount.getText());
                    case DEBIT -> BankingRequest.debit(requestId.getText(), source.getText(), amount.getText());
                    case TRANSFER -> BankingRequest.transfer(requestId.getText(), source.getText(),
                        destination.getText(), amount.getText());
                };
            } catch (IllegalArgumentException ex) {
                feedback.setText(ex.getMessage());
                return;
            }
            submit.setDisable(true);
            runAsync(() -> service.submit(actor, request), result -> {
                if (result.pendingApproval()) {
                    receipt.setText("PENDING SUPERVISOR APPROVAL\n" +
                        "Approval ID: " + result.approvalId() + "\n" +
                        "Request ID: " + request.requestId() + "\n" +
                        "No funds have moved. A supervisor must approve this request.");
                    feedback.setText("Approval request recorded. No balance changed.");
                } else {
                    receipt.setText(formatReceipt(result.receipt()));
                    feedback.setText("Committed. Keep the transaction reference for the customer.");
                }
                requestId.setText(UUID.randomUUID().toString()); // Rotate only after a confirmed result.
                submit.setDisable(false);
            }, error -> {
                // Preserve requestId even on unknown network outcome. Safe retry is possible.
                feedback.setText(visibleError(error));
                submit.setDisable(false);
            });
        });
        receipt.setEditable(false);
        receipt.setPrefRowCount(7);
        Label warning = new Label("Simulated funds only. Restricted amounts become pending approvals; " +
            "no funds move before a supervisor approves and execution commits.");
        warning.setWrapText(true);
        VBox contents = new VBox(12, new Label("Operation"), operation,
            new Label("Source account"), source,
            new Label("Destination account"), destination,
            new Label("Amount (NGN)"), amount,
            new Label("Idempotency request ID"), idRow,
            submit, warning, new Label("Last confirmed receipt"), receipt);
        contents.setPadding(new Insets(18));
        return contents;
    }

    private void updateAccountInputs() {
        TransactionType selected = operation.getValue();
        source.setDisable(selected == TransactionType.CREDIT);
        destination.setDisable(selected == TransactionType.DEBIT);
    }

    private Parent historyTab() {
        accountFilter.setPromptText("Account number, or blank for all");
        typeFilter.setValue("All");
        history.getColumns().addAll(
            column("Reference", TransactionReceipt::reference),
            column("Type", r -> r.type().name()),
            column("Source", r -> safe(r.sourceAccountNumber())),
            column("Destination", r -> safe(r.destinationAccountNumber())),
            column("Amount (NGN)", r -> Money.formatMinor(r.amountMinor())),
            column("Timestamp", r -> r.createdAt().toString()),
            column("Employee", TransactionReceipt::employeeId)
        );
        history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        history.setPrefHeight(340);
        startDate.setPromptText("From date (optional)");
        endDate.setPromptText("Through date (optional)");
        Button search = new Button("Search");
        Button previous = new Button("Previous");
        Button next = new Button("Next");
        search.setOnAction(e -> { page = 0; loadHistory(); });
        previous.setOnAction(e -> { if (page > 0) { page--; loadHistory(); } });
        next.setOnAction(e -> { page++; loadHistory(); });
        HBox filters = new HBox(10, accountFilter, typeFilter, search);
        HBox.setHgrow(accountFilter, Priority.ALWAYS);
        HBox paging = new HBox(10, previous, pageLabel, next);
        HBox dateFilters = new HBox(10, new Label("From"), startDate, new Label("Through"), endDate);
        VBox contents = new VBox(12, filters, dateFilters, history, paging);
        contents.setPadding(new Insets(14));
        return contents;
    }

    private static <T> TableColumn<TransactionReceipt, String> column(
        String name, java.util.function.Function<TransactionReceipt, String> accessor) {
        TableColumn<TransactionReceipt, String> result = new TableColumn<>(name);
        result.setCellValueFactory(v -> new ReadOnlyStringWrapper(accessor.apply(v.getValue())));
        return result;
    }

    private void loadHistory() {
        String accountNumber = accountFilter.getText();
        String chosen = typeFilter.getValue();
        TransactionType type = "All".equals(chosen) ? null : TransactionType.valueOf(chosen);
        int capturedPage = page;
        Instant from = startDate.getValue() == null ? null
            : startDate.getValue().atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant through = endDate.getValue() == null ? null
            : endDate.getValue().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1);
        runAsync(() -> service.history(actor, accountNumber, type, from, through, capturedPage, 30), rows -> {
            history.setItems(FXCollections.observableArrayList(rows));
            pageLabel.setText("Page " + (capturedPage + 1) + " · " + rows.size() + " records");
            feedback.setText("History loaded.");
        }, error -> feedback.setText(visibleError(error)));
    }

    private static String safe(String value) { return value == null ? "—" : value; }

    private static String formatReceipt(TransactionReceipt r) {
        String sourceBalance = r.sourceBalanceAfterMinor() == null ? "—"
            : Money.formatMinor(r.sourceBalanceAfterMinor());
        String destinationBalance = r.destinationBalanceAfterMinor() == null ? "—"
            : Money.formatMinor(r.destinationBalanceAfterMinor());
        return "Reference: " + r.reference() + "\n" +
            "Request ID: " + r.requestId() + "\n" +
            "Type: " + r.type() + "\n" +
            "Amount: NGN " + Money.formatMinor(r.amountMinor()) + "\n" +
            "Source balance after: " + sourceBalance + "\n" +
            "Destination balance after: " + destinationBalance + "\n" +
            "Committed: " + r.createdAt();
    }

    private static String visibleError(Throwable failure) {
        if (failure instanceof IllegalArgumentException || failure instanceof SecurityException
            || failure instanceof TransactionRepository.OutcomeUnknownException) {
            return failure.getMessage();
        }
        return "Operation not confirmed. Keep the same request ID and check transaction history " +
            "before retrying; contact the supervisor if you cannot verify the outcome.";
    }

    private <T> void runAsync(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        feedback.setText("Processing in database...");
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(e -> success.accept(task.getValue()));
        task.setOnFailed(e -> failure.accept(task.getException()));
        Thread thread = new Thread(task, "banking-transaction-worker");
        thread.setDaemon(true);
        thread.start();
    }
}
