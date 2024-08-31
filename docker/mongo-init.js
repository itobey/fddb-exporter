db = db.getSiblingDB('fddb');
db.createUser({
  user: "mongodb_fddb_user",
  pwd: "mongodb_fddb_password",
  roles: [
    { role: "readWrite", db: "fddb" }
  ]
});
