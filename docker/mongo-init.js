db = db.getSiblingDB('fddb');
db.createUser({
  user: "fddb_user",
  pwd: "fddb_password",
  roles: [
    { role: "readWrite", db: "fddb" }
  ]
});
