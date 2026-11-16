package com.banking.config;

/** Environment-only configuration: never check real secrets into Git. */
public record AppConfig(String mongoUri, String mongoDatabase) {
    public static AppConfig fromEnvironment() {
        String uri = System.getenv("MONGODB_URI");
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("MONGODB_URI is not configured. See README.md.");
        }
        if (!(uri.startsWith("mongodb+srv://") || uri.startsWith("mongodb://"))) {
            throw new IllegalArgumentException("MONGODB_URI must be a MongoDB connection string.");
        }
        String database = System.getenv().getOrDefault("MONGODB_DATABASE", "banking_simulation");
        if (!database.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid MONGODB_DATABASE.");
        }
        return new AppConfig(uri, database);
    }
}
