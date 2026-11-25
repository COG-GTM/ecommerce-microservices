db.getSiblingDB(process.env.MONGO_APP_DATABASE).createUser({
  user: process.env.MONGO_APP_USER,
  pwd: process.env.MONGO_APP_PASSWORD,
  roles: [{ role: "readWrite", db: process.env.MONGO_APP_DATABASE }],
});
