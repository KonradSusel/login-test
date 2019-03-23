CREATE TABLE "sessioninfo" (
  "id" SERIAL,
  "username" TEXT,
  "sessionid" TEXT,
  PRIMARY KEY ("id")
);

CREATE TABLE "users" (
  "id" SERIAL,
  "username" TEXT,
  "password" TEXT,
  PRIMARY KEY ("id")
);