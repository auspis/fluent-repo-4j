DROP TABLE IF EXISTS "users";

CREATE TABLE "users" (
    "id" BIGINT PRIMARY KEY AUTO_INCREMENT,
    "user_name" VARCHAR(255) NOT NULL,
    "email" VARCHAR(255) UNIQUE,
    "age" INT
);
