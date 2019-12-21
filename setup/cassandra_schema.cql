CREATE KEYSPACE ledger WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 2 };
CREATE TABLE ledger.account (
  account_id int,
  status text,
  balance decimal,
  PRIMARY KEY (account_id)
);

CREATE TABLE ledger.account_entry (
  account_id int,
  seq_number int,
  entry_date timestamp,
  entry_reason text,
  correlation_id uuid,
  amount decimal,
  PRIMARY KEY (account_id, seq_number)
)
WITH CLUSTERING ORDER BY (seq_number ASC);

--TODO: create a transfer sync project to read from rabbitmq and write in this table:
CREATE TABLE ledger.transfer (
  tx_id uuid,
  from_account int,
  to_account int,
  transfer_date timestamp,
  amount decimal,
  PRIMARY KEY (tx_id)
);
