package com.banking.ui;

import com.banking.models.*;
import com.banking.repositories.*;
import com.banking.security.Authorization;
import com.banking.security.Permission;
import com.banking.services.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** A responsive customer-care UI; all access decisions are repeated by TicketService. */
public final class TicketView {
    private final AuthenticatedEmployee actor;
    private final Runnable back;
    private final TicketService service;
    private final Label feedback = new Label();
    private final Label pageInfo = new Label("Page 1");
    private final TextField query = new TextField();
    private final ComboBox<TicketStatus> filter = new ComboBox<>();
    private final TableView<SupportTicket> table = new TableView<>();
    private final TextArea history = new TextArea();
    private final Label selectedInfo = new Label("Select a ticket to view its details.");
    private final TextArea response = new TextArea();
    private final TextField assignId = new TextField();
    private final ComboBox<TicketStatus> targetStatus = new ComboBox<>();
    private int page = 0;

    public TicketView(MongoClient client, MongoDatabase database, AuthenticatedEmployee actor, Runnable back) {
        this.actor = actor;
        this.back = back;
        EmployeeRepository employees = new EmployeeRepository(database);
        service = new TicketService(new TicketRepository(client, database),
            new CustomerRepository(database), new AccountRepository(database), employees,
            new EmployeeAccess(employees), database);
    }

    public Parent build() {
        Label title = new Label("Customer care & complaints");
        title.getStyleClass().add("page-title");
        Button backButton = new Button("Back to dashboard");
        backButton.setOnAction(e -> back.run());
        HBox heading = new HBox(15, title, new Region(), backButton);
        HBox.setHgrow(heading.getChildren().get(1), Priority.ALWAYS);
        configureTable();
        TabPane tabs = new TabPane(new Tab("Find & manage", managementPane()),
            new Tab("Register complaint", creationPane()));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        feedback.setWrapText(true);
        feedback.getStyleClass().add("subtle");
        VBox content = new VBox(16, heading, tabs, feedback);
        content.setPadding(new Insets(24));
        content.getStyleClass().add("form-card");
        VBox.setVgrow(tabs, Priority.ALWAYS);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        BorderPane root = new BorderPane(scroll);
        root.getStyleClass().add("app-background");
        root.setPadding(new Insets(15));
        reload();
        return root;
    }

    private void configureTable() {
        table.getColumns().addAll(
            column("Ticket #", SupportTicket::ticketNumber),
            column("Customer", SupportTicket::customerId),
            column("Category", t -> t.category().name()),
            column("Status", t -> t.status().name()),
            column("Assigned", t -> t.assignedOfficerId() == null ? "—" : t.assignedOfficerId()),
            column("Created", t -> t.createdAt().toString())
        );
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(240);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, current) -> {
            if (current == null) { selectedInfo.setText("Select a ticket."); history.clear(); return; }
            selectedInfo.setText("Ticket: " + current.ticketNumber() + "\n" +
                "Customer ID: " + current.customerId() + " | Account: " + current.accountNumber() + "\n" +
                "Transaction: " + (current.transactionReference() == null ? "N/A" : current.transactionReference()) + "\n" +
                "Status: " + current.status() + " | Created by: " + current.createdBy() + "\n" +
                "Complaint: " + current.description());
            assignId.setText(current.assignedOfficerId() == null ? "" : current.assignedOfficerId());
            targetStatus.setValue(null);
            loadHistory(current.ticketNumber());
        });
    }

    private Parent managementPane() {
        query.setPromptText("Exact ticket number, customer ID, account number or transaction reference");
        filter.setItems(FXCollections.observableArrayList(TicketStatus.values()));
        filter.setPromptText("All statuses");
        Button find = new Button("Search");
        find.setOnAction(e -> {page = 0; reload();});
        Button clearFilter = new Button("Clear filters");
        clearFilter.setOnAction(e -> {query.clear(); filter.setValue(null); page=0; reload();});
        HBox search = new HBox(10, query, filter, find, clearFilter);
        HBox.setHgrow(query, Priority.ALWAYS);
        Button previous = new Button("Previous");
        Button next = new Button("Next");
        previous.setOnAction(e -> { if (page > 0) {page--; reload();} });
        next.setOnAction(e -> {page++; reload();});
        HBox pagination = new HBox(12, previous, pageInfo, next);

        selectedInfo.setWrapText(true);
        history.setEditable(false); history.setPrefRowCount(5); history.setWrapText(true);
        response.setPromptText("Customer care response / resolution notes (required when changing status)");
        response.setPrefRowCount(3); response.setWrapText(true);
        Button reply = new Button("Add response");
        reply.setOnAction(e -> {
            SupportTicket selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {feedback.setText("Select a ticket first."); return;}
            String body = response.getText();
            run(() -> service.respond(actor, selected.ticketNumber(), body), changed -> {
                response.clear(); refreshSelected(changed);
                feedback.setText("Response saved.");
            });
        });
        targetStatus.setItems(FXCollections.observableArrayList(
            TicketStatus.IN_PROGRESS, TicketStatus.ESCALATED, TicketStatus.RESOLVED));
        targetStatus.setPromptText("New status");
        Button changeStatus = new Button("Update status");
        changeStatus.setOnAction(e -> {
            SupportTicket selected = table.getSelectionModel().getSelectedItem();
            if (selected == null || targetStatus.getValue() == null) {
                feedback.setText("Select a ticket and a new status."); return;
            }
            TicketStatus newStatus = targetStatus.getValue();
            String body = response.getText();
            run(() -> service.setStatus(actor, selected.ticketNumber(), newStatus, body), changed -> {
                response.clear(); refreshSelected(changed);
                feedback.setText("Ticket status updated.");
            });
        });
        HBox update = new HBox(12, reply, targetStatus, changeStatus);

        assignId.setPromptText("Customer care employee ID");
        Button take = new Button("Assign to me");
        Button assign = new Button("Assign officer");
        take.setOnAction(e -> assignTo(actor.id()));
        assign.setOnAction(e -> assignTo(assignId.getText()));
        HBox assignment = new HBox(12, assignId, take, assign);
        HBox.setHgrow(assignId, Priority.ALWAYS);
        Label note = new Label("An officer can take a ticket for themselves. Only supervisors can assign other officers. " +
            "An escalated ticket must be handled by a supervisor.");
        note.setWrapText(true);
        VBox pane = new VBox(12, search, table, pagination, selectedInfo, new Label("Response history"),
            history, response, update, new Label("Assign ticket"), assignment, note);
        pane.setPadding(new Insets(16));
        return pane;
    }

    private Parent creationPane() {
        TextField customer = new TextField(); customer.setPromptText("Customer ID");
        TextField account = new TextField(); account.setPromptText("Account number");
        TextField reference = new TextField(); reference.setPromptText("Transaction reference (for transaction complaints)");
        ComboBox<TicketCategory> category = new ComboBox<>(FXCollections.observableArrayList(TicketCategory.values()));
        category.setValue(TicketCategory.GENERAL_INQUIRY);
        TextArea description = new TextArea(); description.setWrapText(true);
        description.setPromptText("Describe the customer's complaint"); description.setPrefRowCount(5);
        Button create = new Button("Register complaint"); create.getStyleClass().add("primary");
        create.setOnAction(e -> {
            String customerId = customer.getText(), accountNumber = account.getText();
            String transactionReference = reference.getText(), body = description.getText();
            TicketCategory selected = category.getValue();
            run(() -> service.register(actor, customerId, accountNumber,
                transactionReference, selected, body), ticket -> {
                    description.clear(); reference.clear();
                    feedback.setText("Ticket registered: " + ticket.ticketNumber());
                    page=0; reload();
                });
        });
        Label explanation = new Label("Ticket creation validates customer-account ownership. " +
            "Transaction complaints require a reference linked to the selected account. " +
            "A notification is queued after database commit.");
        explanation.setWrapText(true);
        VBox pane = new VBox(12, explanation, new Label("Customer"), customer,
            new Label("Account"), account, new Label("Transaction (if applicable)"), reference,
            new Label("Category"), category, new Label("Description"), description, create);
        pane.setPadding(new Insets(20));
        return pane;
    }

    private void assignTo(String officerId) {
        SupportTicket selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {feedback.setText("Select a ticket first."); return;}
        run(() -> service.assign(actor, selected.ticketNumber(), officerId), changed -> {
            refreshSelected(changed); feedback.setText("Ticket assigned.");
        });
    }

    private void refreshSelected(SupportTicket changed) {
        reload();
        loadHistory(changed.ticketNumber());
    }

    private void loadHistory(String ticketNumber) {
        run(() -> service.history(actor, ticketNumber, 0, 100), items -> {
            StringBuilder text = new StringBuilder();
            for (TicketMessage item : items) text.append(item.createdAt()).append(" | ")
                .append(item.employeeId()).append("\n").append(item.body()).append("\n\n");
            history.setText(text.isEmpty() ? "No responses yet." : text.toString());
        });
    }

    private void reload() {
        String term = query.getText(); TicketStatus statusFilter = filter.getValue(); int requestedPage=page;
        run(() -> service.search(actor, term, statusFilter, requestedPage, 50), items -> {
            table.setItems(FXCollections.observableArrayList(items));
            pageInfo.setText("Page " + (page + 1) + " — " + items.size() + " item(s)");
        });
    }

    private <T> void run(Callable<T> action, Consumer<T> success) {
        feedback.setText("Working...");
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {return action.call();}
        };
        task.setOnSucceeded(e -> success.accept(task.getValue()));
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            feedback.setText(error instanceof IllegalArgumentException || error instanceof SecurityException
                || error instanceof IllegalStateException ? error.getMessage() :
                "Request could not be completed. Verify MongoDB connectivity and try refreshing.");
        });
        Thread thread = new Thread(task, "ticket-service-worker");
        thread.setDaemon(true);
        thread.start();
    }

    private static <T> TableColumn<T, String> column(String title, Function<T, String> mapper) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(mapper.apply(cell.getValue())));
        return column;
    }
}
