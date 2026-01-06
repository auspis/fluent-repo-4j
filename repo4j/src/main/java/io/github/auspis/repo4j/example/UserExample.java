package io.github.auspis.repo4j.example;

import io.github.auspis.repo4j.core.ConnectionProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * Esempio di utilizzo del Repository Pattern con JDBC puro.
 * Dimostra il ciclo di vita della Connection e l'uso dei metodi CRUD.
 */
public class UserExample {

    public static void main(String[] args) throws Exception {
        // Passo 1: Crea una connessione (di solito da DataSource/Connection Pool)
        Connection connection = DriverManager.getConnection("jdbc:h2:mem:example", "sa", "");

        // Passo 2: Crea lo schema (di solito fatto una volta)
        createSchema(connection);

        // Passo 3: Imposta la connection nel provider (ThreadLocal)
        ConnectionProvider.setConnection(connection);

        try {
            // Passo 4: Usa il repository - Zero Connection in firma!
            UserRepository userRepository = new UserRepository();

            System.out.println("=== Create ===");
            User user1 = userRepository.create(new User("Alice", "alice@example.com"));
            System.out.println("Creato: " + user1);

            User user2 = userRepository.create(new User("Bob", "bob@example.com"));
            System.out.println("Creato: " + user2);

            System.out.println("\n=== FindById ===");
            Optional<User> found = userRepository.findById(user1.getId());
            System.out.println("Trovato: " + found);

            System.out.println("\n=== FindAll ===");
            List<User> allUsers = userRepository.findAll();
            System.out.println("Tutti gli utenti: " + allUsers);

            System.out.println("\n=== Update ===");
            user1.setName("Alice Updated");
            user1.setEmail("alice.updated@example.com");
            User updated = userRepository.update(user1);
            System.out.println("Aggiornato: " + updated);

            System.out.println("\n=== FindByEmail (custom query) ===");
            Optional<User> foundByEmail = userRepository.findByEmail("bob@example.com");
            System.out.println("Trovato per email: " + foundByEmail);

            System.out.println("\n=== FindByNameLike (custom query) ===");
            userRepository.create(new User("Alicia", "alicia@example.com"));
            List<User> likeAli = userRepository.findByNameLike("Ali");
            System.out.println("Utenti che contengono 'Ali': " + likeAli);

            System.out.println("\n=== Delete ===");
            userRepository.delete(user2.getId());
            System.out.println("Eliminato utente con ID: " + user2.getId());

            System.out.println("\n=== FindAll dopo delete ===");
            allUsers = userRepository.findAll();
            System.out.println("Utenti rimanenti: " + allUsers);

        } finally {
            // Passo 5: Pulisci la connection
            ConnectionProvider.close();
        }

        System.out.println("\n✅ Esempio completato con successo!");
    }

    private static void createSchema(Connection connection) throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE users (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "name VARCHAR(255) NOT NULL," +
                    "email VARCHAR(255) UNIQUE NOT NULL" +
                    ")");
        }
    }
}
