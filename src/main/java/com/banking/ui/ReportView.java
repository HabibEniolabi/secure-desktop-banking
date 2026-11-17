package com.banking.ui;

import com.banking.models.AuthenticatedEmployee;
import com.banking.models.ReportPeriod;
import com.banking.models.ReportSnapshot;
import com.banking.models.ReportSnapshot.AuditRow;
import com.banking.models.ReportSnapshot.TransactionRow;
import com.banking.repositories.EmployeeRepository;
import com.banking.security.Authorization;
import com.banking.security.Permission;
import com.banking.services.EmployeeAccess;
import com.banking.services.ReportService;
import com.banking.utils.Money;
import com.mongodb.client.MongoDatabase;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

/** Read-only operational dashboard. All MongoDB calls are made on a worker thread. */
public final class ReportView {
    private static final DateTimeFormatter UTC_DATE =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final AuthenticatedEmployee actor;
    private final Runnable back;
    private final ReportService service;
    private final DatePicker from = new DatePicker(ReportPeriod.todayUtc().startDate());
    private final DatePicker to = new DatePicker(ReportPeriod.todayUtc().endDate());
    private final Label feedback = new Label("Select dates and refresh to load reports.");
    private final Label[] metrics = new Label[8];
    private final TableView<TransactionRow> transactions = new TableView<>();
    private final TableView<AuditRow> audit = new TableView<>();
    private final boolean canViewAudit;
    private long refreshSequence;

    public ReportView(MongoDatabase database, AuthenticatedEmployee actor, Runnable back) {
        this.actor = actor;
        this.back = back;
        this.service = new ReportService(database, new EmployeeAccess(new EmployeeRepository(database)));
        canViewAudit = Authorization.can(actor.role(), Permission.VIEW_AUDIT_LOGS);
    }

    public Parent build() {
        Label heading = new Label("Operational dashboard & reports");
        heading.getStyleClass().add("page-title");
        Button backButton = new Button("Back to dashboard");
        backButton.setOnAction(e -> back.run());
        HBox header = new HBox(14, heading, new Region(), backButton);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);

        from.setPromptText("Start date (UTC)");
        to.setPromptText("End date (UTC)");
        Button refresh = new Button("Refresh report");
        refresh.getStyleClass().add("primary");
        refresh.setOnAction(e -> load());
        HBox controls = new HBox(10, new Label("From (UTC)"), from,
            new Label("Through (UTC)"), to, refresh);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(6, 0, 6, 0));

        Label explanation = new Label("Customer/account/ticket/approval counts are current; monetary totals " +
            "and activity tables reflect the selected UTC dates. Internal transfers are shown separately.");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("subtle");

        GridPane tiles = new GridPane();
        tiles.setHgap(12);
        tiles.setVgap(12);
        String[] headings = {
            "Customers", "Active accounts", "Frozen accounts", "Unresolved complaints",
            "Pending approvals", "Credits in period", "Debits in period", "Transfers in period"
        };
        for (int i = 0; i < headings.length; i++) {
            Label metric = new Label("—");
            metric.getStyleClass().add("stat-value");
            metrics[i] = metric;
            Label caption = new Label(headings[i]);
            caption.getStyleClass().add("stat-label");
            VBox tile = new VBox(7, caption, metric);
            tile.getStyleClass().add("stat-tile");
            tile.setMinWidth(265);
            tiles.add(tile, i % 2, i / 2);
        }

        transactions.getColumns().addAll(
            column("Reference", TransactionRow::reference),
            column("Type", TransactionRow::type),
            column("Amount (NGN)", row -> Money.formatMinor(row.amountMinor())),
            column("Source", row -> masked(row.sourceAccount())),
            column("Destination", row -> masked(row.destinationAccount())),
            column("Employee ID", TransactionRow::employeeId),
            column("Time", row -> UTC_DATE.format(row.createdAt())));
        transactions.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        transactions.setPrefHeight(270);
        transactions.setPlaceholder(new Label("No completed transactions in the selected date range."));

        TabPane tables = new TabPane();
        Tab completed = new Tab("Recent completed transactions (up to 30)", transactions);
        completed.setClosable(false);
        tables.getTabs().add(completed);
        if (canViewAudit) {
            audit.getColumns().addAll(
                column("Event", AuditRow::event),
                column("Employee ID", AuditRow::actorId),
                column("Reference / target", AuditRow::target),
                column("Time", row -> UTC_DATE.format(row.createdAt())));
            audit.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            audit.setPrefHeight(270);
            audit.setPlaceholder(new Label("No audit events within the selected date range."));
            Tab events = new Tab("Recent audit activity (up to 30)", audit);
            events.setClosable(false);
            tables.getTabs().add(events);
        }
        VBox.setVgrow(tables, Priority.ALWAYS);
        feedback.setWrapText(true);
        feedback.getStyleClass().add("subtle");
        VBox page = new VBox(17, header, new Separator(), controls, explanation, tiles, tables, feedback);
        page.setPadding(new Insets(28));
        page.getStyleClass().add("form-card");
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        BorderPane root = new BorderPane(scroll);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("app-background");
        load();
        return root;
    }

    private void load() {
        ReportPeriod period;
        try { period = new ReportPeriod(from.getValue(), to.getValue()); }
        catch (RuntimeException error) { feedback.setText(error.getMessage()); return; }
        final long token = ++refreshSequence;
        feedback.setText("Loading dashboard from MongoDB...");
        async(() -> service.load(actor, period), snapshot -> {
            if (token != refreshSequence) return;
            metrics[0].setText(Long.toString(snapshot.customerCount()));
            metrics[1].setText(Long.toString(snapshot.activeAccounts()));
            metrics[2].setText(Long.toString(snapshot.frozenAccounts()));
            metrics[3].setText(Long.toString(snapshot.unresolvedComplaints()));
            metrics[4].setText(Long.toString(snapshot.pendingApprovals()));
            metrics[5].setText("₦" + Money.formatMinor(snapshot.creditMinor()));
            metrics[6].setText("₦" + Money.formatMinor(snapshot.debitMinor()));
            metrics[7].setText("₦" + Money.formatMinor(snapshot.transferMinor()));
            transactions.setItems(FXCollections.observableArrayList(snapshot.recentTransactions()));
            feedback.setText("Report loaded for " + period.startDate() + " to " + period.endDate() +
                " (UTC). " + snapshot.recentTransactions().size() + " recent transactions shown.");
        }, error -> { if (token == refreshSequence) feedback.setText(safeError(error)); });
        if (canViewAudit) {
            async(() -> service.recentAudit(actor, period), events -> {
                if (token == refreshSequence) audit.setItems(FXCollections.observableArrayList(events));
            }, error -> {
                if (token == refreshSequence) feedback.setText(safeError(error));
            });
        }
    }

    private static String masked(String account) {
        return account == null || account.isBlank() ? "—" :
            (account.length() <= 4 ? "••••" : "••••••" + account.substring(account.length() - 4));
    }

    private static String safeError(Throwable error) {
        return error instanceof IllegalArgumentException || error instanceof SecurityException
            ? error.getMessage() : "Unable to retrieve report. Check database connectivity and try again.";
    }

    private static <T> TableColumn<T, String> column(String name, Function<T, String> getter) {
        TableColumn<T, String> result = new TableColumn<>(name);
        result.setCellValueFactory(item -> new ReadOnlyStringWrapper(
            getter.apply(item.getValue()) == null ? "—" : getter.apply(item.getValue())));
        return result;
    }

    private static <T> void async(Callable<T> query, Consumer<T> success, Consumer<Throwable> failure) {
        Task<T> worker = new Task<>() {
            @Override protected T call() throws Exception { return query.call(); }
        };
        worker.setOnSucceeded(e -> success.accept(worker.getValue()));
        worker.setOnFailed(e -> failure.accept(worker.getException()));
        Thread thread = new Thread(worker, "banking-report-worker");
        thread.setDaemon(true);
        thread.start();
    }
}
