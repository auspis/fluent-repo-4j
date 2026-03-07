DROP TABLE IF EXISTS "users";

CREATE TABLE "users" (
    "id" INTEGER PRIMARY KEY AUTO_INCREMENT,
    "name" VARCHAR(50),
    "email" VARCHAR(100),
    "age" INTEGER,
    "active" BOOLEAN,
    "birthdate" DATE,
    "createdAt" TIMESTAMP,
    "address" JSON,
    "preferences" JSON
);
