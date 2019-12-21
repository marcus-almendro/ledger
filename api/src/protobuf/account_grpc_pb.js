// GENERATED CODE -- DO NOT EDIT!

'use strict';
var grpc = require('grpc');
var account_pb = require('./account_pb.js');

function serialize_account_AccountMessage(arg) {
  if (!(arg instanceof account_pb.AccountMessage)) {
    throw new Error('Expected argument of type account.AccountMessage');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_account_AccountMessage(buffer_arg) {
  return account_pb.AccountMessage.deserializeBinary(new Uint8Array(buffer_arg));
}


var AccountServiceService = exports.AccountServiceService = {
  ask: {
    path: '/account.AccountService/ask',
    requestStream: false,
    responseStream: false,
    requestType: account_pb.AccountMessage,
    responseType: account_pb.AccountMessage,
    requestSerialize: serialize_account_AccountMessage,
    requestDeserialize: deserialize_account_AccountMessage,
    responseSerialize: serialize_account_AccountMessage,
    responseDeserialize: deserialize_account_AccountMessage,
  },
};

exports.AccountServiceClient = grpc.makeGenericClientConstructor(AccountServiceService);
