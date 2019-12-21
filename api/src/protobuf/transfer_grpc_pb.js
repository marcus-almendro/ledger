// GENERATED CODE -- DO NOT EDIT!

'use strict';
var grpc = require('grpc');
var transfer_pb = require('./transfer_pb.js');

function serialize_transfer_TransferMessage(arg) {
  if (!(arg instanceof transfer_pb.TransferMessage)) {
    throw new Error('Expected argument of type transfer.TransferMessage');
  }
  return Buffer.from(arg.serializeBinary());
}

function deserialize_transfer_TransferMessage(buffer_arg) {
  return transfer_pb.TransferMessage.deserializeBinary(new Uint8Array(buffer_arg));
}


var TransferServiceService = exports.TransferServiceService = {
  ask: {
    path: '/transfer.TransferService/ask',
    requestStream: false,
    responseStream: false,
    requestType: transfer_pb.TransferMessage,
    responseType: transfer_pb.TransferMessage,
    requestSerialize: serialize_transfer_TransferMessage,
    requestDeserialize: deserialize_transfer_TransferMessage,
    responseSerialize: serialize_transfer_TransferMessage,
    responseDeserialize: deserialize_transfer_TransferMessage,
  },
};

exports.TransferServiceClient = grpc.makeGenericClientConstructor(TransferServiceService);
