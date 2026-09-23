package com.hr.recruitment.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import javax.sql.DataSource;

/**
 * Builds the shared HikariCP pool (size 2, matching proposal.md R4: pool 2 x reserved
 * concurrency 5 = at most 10 connections). Credentials come from Secrets Manager when
 * DB_SECRET_ARN is set (AWS), or from DB_PASSWORD locally.
 */
public final class DataSourceFactory {

    private DataSourceFactory() {}

    public static DataSource create() {
        String host = require("DB_HOST");
        String port = System.getenv().getOrDefault("DB_PORT", "5432");
        String dbName = require("DB_NAME");
        String secretArn = System.getenv("DB_SECRET_ARN");

        String user;
        String password;
        if (secretArn != null && !secretArn.isBlank()) {
            Credentials creds = fetchFromSecretsManager(secretArn);
            user = creds.username();
            password = creds.password();
        } else {
            user = System.getenv().getOrDefault("DB_USER", "postgres");
            password = require("DB_PASSWORD");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + dbName);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setPoolName("hr-recruitment-pool");
        return new HikariDataSource(config);
    }

    private static Credentials fetchFromSecretsManager(String secretArn) {
        try (SecretsManagerClient client = SecretsManagerClient.create()) {
            String secretJson = client.getSecretValue(GetSecretValueRequest.builder()
                .secretId(secretArn)
                .build()).secretString();
            JsonNode node = Json.MAPPER.readTree(secretJson);
            return new Credentials(node.get("username").asText(), node.get("password").asText());
        } catch (Exception e) {
            // Never log the exception message here in case it echoes secret content.
            throw new IllegalStateException("Unable to read DB credentials from Secrets Manager");
        }
    }

    private static String require(String envVar) {
        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + envVar);
        }
        return value;
    }

    private record Credentials(String username, String password) {}
}
