package com.banking.repositories;

import com.banking.models.AccountStatus;
import com.banking.models.BankAccount;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public final class AccountRepository {
    private final MongoCollection<Document> accounts;

    public AccountRepository(MongoDatabase database) { accounts = database.getCollection("accounts"); }

    public Optional<BankAccount> findByNumber(String accountNumber) {
        return Optional.ofNullable(accounts.find(eq("accountNumber", accountNumber)).first()).map(AccountRepository::map);
    }

    public List<BankAccount> byCustomer(String customerId, int page, int size) {
        List<BankAccount> result = new ArrayList<>();
        accounts.find(eq("customerId", customerId)).sort(Sorts.ascending("accountNumber"))
            .skip(Math.multiplyExact(page, size)).limit(size).forEach(d -> result.add(map(d)));
        return result;
    }

    public BankAccount create(String customerId, String accountNumber) {
        Date now = new Date();
        Document doc = new Document("accountNumber", accountNumber).append("customerId", customerId)
            .append("balanceMinor", 0L).append("status", AccountStatus.ACTIVE.name())
            .append("createdAt", now).append("updatedAt", now);
        try { accounts.insertOne(doc); }
        catch (MongoWriteException ex) {
            if (ex.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) throw new AccountNumberCollisionException(ex);
            throw ex;
        }
        return map(doc);
    }

    /** Atomic state transition; closed accounts cannot be reopened. Closing requires zero balance. */
    public Optional<BankAccount> changeStatus(String accountNumber, AccountStatus status) {
        var criteria = status == AccountStatus.CLOSED
            ? and(eq("accountNumber", accountNumber), ne("status", AccountStatus.CLOSED.name()), eq("balanceMinor", 0L))
            : and(eq("accountNumber", accountNumber), ne("status", AccountStatus.CLOSED.name()));
        Document modified = accounts.findOneAndUpdate(criteria,
            combine(set("status", status.name()), set("updatedAt", new Date())),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        return Optional.ofNullable(modified).map(AccountRepository::map);
    }

    public static final class AccountNumberCollisionException extends RuntimeException {
        public AccountNumberCollisionException(Throwable cause) { super(cause); }
    }

    private static BankAccount map(Document d) {
        return new BankAccount(d.getString("accountNumber"), d.getString("customerId"),
            d.getLong("balanceMinor"), AccountStatus.valueOf(d.getString("status")),
            d.getDate("createdAt").toInstant(), d.getDate("updatedAt").toInstant());
    }
}
