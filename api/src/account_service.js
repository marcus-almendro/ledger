import { createRequire } from 'module';
const require = createRequire(import.meta.url);
const messages = require('./protobuf/account_pb');
const service = require('./protobuf/account_grpc_pb.js');
const uuid = require('uuid-random');
const grpc = require('grpc');
const cassandra = require('cassandra-driver');
const cassandraClient = new cassandra.Client({ contactPoints: ['cassandra1'], localDataCenter: 'datacenter1', keyspace: 'ledger' });

const client = new service.AccountServiceClient(process.env.ACCOUNT_SERVICE_ADDRESS, grpc.credentials.createInsecure());

function openAccount(req, res) {
  const msg = new messages.AccountMessage();
  const openAccount = new messages.OpenAccount();
  openAccount.setInitialamount(req.body.initialAmount);
  msg.setAccountid(req.body.accountId);
  msg.setCorrelationid(req.body.correlationId);
  msg.setOpenaccount(openAccount);
  sendAccountRequest(msg, res);
}

function closeAccount(req, res) {
  const msg = new messages.AccountMessage();
  msg.setAccountid(req.params.accountId);
  msg.setCorrelationid(uuid());
  msg.setCloseaccount(new messages.CloseAccount());
  sendAccountRequest(msg, res);
}

const getAccountQuery = 'SELECT * FROM ledger.account WHERE account_id = ?';
function getAccount(req, res) {
  cassandraClient.execute(getAccountQuery, [ req.params.accountId ], { prepare : true }, function(err, result) {
    if(err)
      res.status(500).send({error: err});
    else
      res.send(result.rows[0]);
  });
}

const getAccountEntriesQuery = 'SELECT * FROM ledger.account_entry WHERE account_id = ?';
function getAccountEntries(req, res) {
  cassandraClient.execute(getAccountEntriesQuery, [ req.params.accountId ], { prepare : true }, function(err, result) {
    if(err)
      res.status(500).send({error: err});
    else
      res.send(result.rows);
  });
}

function sendAccountRequest(msg, res) {
  client.ask(msg, (err, response) => {
    if (err)
      res.status(500).send({error: err});
    else {
      const r = sanitize(response);
      res.status(r.status).send(r.body);
    }
  });
}

function sanitize(msg) {
  const msgType = msg.getMsgtypeCase();

  if (msgType == messages.AccountMessage.MsgtypeCase.ACCOUNTOPENED)
    return { status: 200, body: { timestamp: msg.getAccountopened().getTimestamp() } };

  if (msgType == messages.AccountMessage.MsgtypeCase.ACCOUNTCLOSED)
    return { status: 200, body: { timestamp: msg.getAccountclosed().getTimestamp() } };
    
  if (msgType == messages.AccountMessage.MsgtypeCase.INVALIDOPERATION)
    return { status: 422, body: { code: msgType, message: msg.getInvalidoperation().getReason() }};
}

export { 
  getAccount,
  getAccountEntries,
  openAccount,
  closeAccount
};

