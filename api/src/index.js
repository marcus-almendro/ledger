import { createRequire } from 'module';
import { openAccount, getAccount, closeAccount, getAccountEntries } from './account_service.js';
import { sendTransfer } from './transfer_service.js';
const require = createRequire(import.meta.url);
const express = require('express');
const app = express();

app.use(express.json());

app.post('/accounts', openAccount);
app.get('/accounts/:accountId', getAccount);
app.get('/accounts/:accountId/entries', getAccountEntries);
app.delete('/accounts/:accountId', closeAccount);
app.post('/transfers', sendTransfer);

app.listen(3000,  () => {
  console.log('Ledger API listening on port 3000!');
});

