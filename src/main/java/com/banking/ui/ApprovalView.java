package com.banking.ui;

import com.banking.models.ApprovalTicket;
import com.banking.models.AuthenticatedEmployee;
import com.banking.repositories.EmployeeRepository;
import com.banking.repositories.TransactionRepository;
import com.banking.services.ApprovalService;
import com.banking.services.EmployeeAccess;
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

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** Accessible only to supervisors; DB-backed authorization repeats inside the service. */
public final class ApprovalView {
    private final AuthenticatedEmployee actor;
    private final Runnable back;
    private final ApprovalService service;
    private final TableView<ApprovalTicket> table = new TableView<>();
    private final Label status = new Label();
    private final Label pageLabel = new Label("Page 1");
    private final TextArea details = new TextArea();
    private int page = 0;

    public ApprovalView(MongoClient client, MongoDatabase database,
                        AuthenticatedEmployee actor, Runnable back) {
        this.actor = actor;
        this.back = back;
        this.service = new ApprovalService(new TransactionRepository(client, database),
            new EmployeeAccess(new EmployeeRepository(database)));
    }

    public Parent build() {
        Label title = new Label("Supervisor approvals");
        title.getStyleClass().add("page-title");
        Button backButton = new Button("Back to dashboard");
        backButton.setOnAction(e -> back.run());
        HBox top = new HBox(15, title, new Region(), backButton);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);

        table.getColumns().addAll(
            column("Approval ID", ApprovalTicket::approvalId),
            column("Type", t -> t.type().name()),
            column("Amount (NGN)", t -> Money.formatMinor(t.amountMinor())),
            column("Initiated by", ApprovalTicket::initiatedBy),
            column("Date", t -> t.createdAt().toString())
        );
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(290);
        details.setEditable(false);
        details.setPrefRowCount(7);
        details.setWrapText(true);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            details.setText(selected == null ? "Select a pending request" :
                "Approval ID: " + selected.approvalId() + "\n" +
                "Request ID: " + selected.requestId() + "\n" +
                "Operation: " + selected.type() + "\n" +
                "Amount: NGN " + Money.formatMinor(selected.amountMinor()) + "\n" +
                "Source: " + (selected.sourceAccountNumber() == null ? "—" : selected.sourceAccountNumber()) + "\n" +
                "Destination: " + (selected.destinationAccountNumber() == null ? "—" : selected.destinationAccountNumber()) + "\n" +
                "Initiator: " + selected.initiatedBy());
        });

        Button reload = new Button("Refresh");
        Button previous = new Button("Previous");
        Button next = new Button("Next");
        reload.setOnAction(e -> { page = 0; load(); });
        previous.setOnAction(e -> { if (page > 0) { page--; load(); } });
        next.setOnAction(e -> { page++; load(); });
        HBox pager = new HBox(12, reload, previous, pageLabel, next);
        Button approve = new Button("Approve and execute");
        approve.getStyleClass().add("primary");
        Button reject = new Button("Reject request");
        approve.setOnAction(e -> decide(true, approve, reject));
        reject.setOnAction(e -> decide(false, approve, reject));
        Label explanation = new Label("Approving executes the financial operation only if the accounts and funds " +
            "are valid at that moment. Rejection never moves money.");
        explanation.setWrapText(true);
        status.setWrapText(true);
        VBox pageLayout = new VBox(13, top, new Separator(), explanation,
            table, pager, details, new HBox(12, approve, reject), status);
        pageLayout.setPadding(new Insets(26));
        pageLayout.getStyleClass().add("form-card");
        ScrollPane scroll = new ScrollPane(pageLayout);
        scroll.setFitToWidth(true);
        BorderPane root = new BorderPane(scroll);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("app-background");
        load();
        return root;
    }

    private void decide(boolean approve, Button accept, Button reject) {
        ApprovalTicket selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) { status.setText("Select a pending approval first."); return; }
        accept.setDisable(true); reject.setDisable(true);
        runAsync(() -> service.decide(actor, selected.approvalId(), approve), result -> {
            status.setText(approve ? "Approved and financially committed: " + result.transactionReference()
                : "Request rejected. No funds moved.");
            accept.setDisable(false); reject.setDisable(false);
            load();
        }, failure -> {
            status.setText(safeFailure(failure));
            accept.setDisable(false); reject.setDisable(false);
        });
    }

    private void load() {
        int requested = page;
        runAsync(() -> service.pending(actor, requested, 30), rows -> {
            table.setItems(FXCollections.observableArrayList(rows));
            pageLabel.setText("Page " + (requested + 1) + " · " + rows.size() + " pending");
            status.setText("Pending approvals loaded.");
        }, failure -> status.setText(safeFailure(failure)));
    }

    private static <T> TableColumn<ApprovalTicket, String> column(String label, Function<ApprovalTicket,String> value) {
        TableColumn<ApprovalTicket, String> col = new TableColumn<>(label);
        col.setCellValueFactory(row -> new ReadOnlyStringWrapper(value.apply(row.getValue())));
        return col;
    }

    private static String safeFailure(Throwable error) {
        return error instanceof IllegalArgumentException || error instanceof SecurityException
            ? error.getMessage() : "Request could not be completed. Check database connectivity and retry carefully.";
    }

    private <T> void runAsync(Callable<T> work, Consumer<T> success, Consumer<Throwable> failed) {
        status.setText("Contacting database...");
        Task<T> worker = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        worker.setOnSucceeded(e -> success.accept(worker.getValue()));
        worker.setOnFailed(e -> failed.accept(worker.getException()));
        Thread thread = new Thread(worker, "supervisor-approval-worker");
        thread.setDaemon(true);
        thread.start();
    }
}
