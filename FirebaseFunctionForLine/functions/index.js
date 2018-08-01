'use strict';

const functions = require('firebase-functions');

const express = require('express');
const bodyParser = require('body-parser');
const expressValidator = require("express-validator");
const cookieParser = require('cookie-parser')();
const cors = require('cors')({ origin: true });

// Firebase Setup
const admin = require('firebase-admin');
// const serviceAccount = functions.config().service_account;
const serviceAccount = require('./service-account.json');
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const app = express();

app.use(bodyParser.json());
app.use(expressValidator([])); // this line must be immediately after any of the bodyParser middlewares!
app.use(cors);
app.use(cookieParser);

// Route
// Endpoint to verify if your Node server is up
app.get('/', (req, res) => {
  return res.status(200).send('Server is up and running!');
});
// Endpoint to verify LINE token and exchange for Firebase Custom Auth token
app.post('/verifyToken', require('./verifyToken').handler);

exports.api = functions.https.onRequest(app);

