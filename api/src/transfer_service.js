import { createRequire } from 'module';
import { checkConnection } from './net_util.js';
const require = createRequire(import.meta.url);
const amqplib = require('amqplib');
const messages = require('./protobuf/transfer_pb');

var channel = null;
const directReplyQ = "amq.rabbitmq.reply-to";
const transfersQ = "transfers.in";
const pendingReplies = {};

checkConnection(process.env.RABBITMQ_HOST, process.env.RABBITMQ_PORT).then(() => {
  amqplib.connect('amqp://' + process.env.RABBITMQ_HOST + ':' + process.env.RABBITMQ_PORT).then(conn => {
    console.info('RabbitMQ Connection OK!');
  
    conn.createConfirmChannel().then(ch => {
      console.info('RabbitMQ Channel OK!');
      channel = ch;
  
      ch.consume(directReplyQ, msg => {
        const obj = messages.TransferMessage.deserializeBinary(msg.content);
        const correlationId = obj.getCorrelationid();
        const r = sanitize(obj);
        const res = pendingReplies[correlationId];
        res.status(r.status).send(r.body);      
        delete pendingReplies[correlationId];
      }, { noAck: true });
      
    }).catch(console.warn);
  }).catch(console.warn);
});

function sendTransfer(req, res) {
  const msg = new messages.TransferMessage();
  const transfer = new messages.Transfer();
  transfer.setFromaccountid(req.body.fromAccountId);
  transfer.setToaccountid(req.body.toAccountId);
  transfer.setAmount(req.body.amount);
  msg.setCorrelationid(req.body.correlationId);
  msg.setTransfer(transfer);

  pendingReplies[req.body.correlationId] = res;

  const binaryMsg = Buffer.from(msg.serializeBinary().buffer);
  channel.sendToQueue(transfersQ, binaryMsg, { replyTo: directReplyQ }, (err, ok) => {
    if (err !== null)
      res.status(500).send(err);
  });
}

function sanitize(msg) {
  const msgType = msg.getMsgtypeCase();

  if (msgType == messages.TransferMessage.MsgtypeCase.TRANSFEREXECUTED)
    return { status: 200, body: { timestamp: msg.getTransferexecuted().getTimestamp() } };

  if (msgType == messages.TransferMessage.MsgtypeCase.TRANSFERFAILEDDUETOWITHDRAWACCOUNT)
    return { status: 422, body: { code: msgType, message: 'Transfer failed due to withdraw account'} };

  if (msgType == messages.TransferMessage.MsgtypeCase.TRANSFERFAILEDDUETODEPOSITACCOUNT)
    return { status: 422, body: { code: msgType, message: 'Transfer failed due to deposit account'} };

  if (msgType == messages.TransferMessage.MsgtypeCase.TRANSFERFAILEDANDCANNOTROLLBACK)
    return { status: 422, body: { code: msgType, message: 'Transfer failed and cannot rollback'} };

  if (msgType == messages.TransferMessage.MsgtypeCase.INVALIDOPERATION)
    return { status: 422, body: { code: msgType, message: msg.getInvalidoperation().getReason() } };
}

export {
  sendTransfer
};

